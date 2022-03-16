import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.bonigarcia.wdm.WebDriverManager
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.*

const val MAX_WAIT = 30L

fun firefox(options: FirefoxOptions): WebDriver {
    WebDriverManager.firefoxdriver().setup()
    return FirefoxDriver(options)
}

fun chrome(options: ChromeOptions): WebDriver {
    WebDriverManager.chromedriver().setup()
    return ChromeDriver(options)
}

fun String?.toFirefoxOptions() =
    this?.let { firefoxPath ->
        FirefoxOptions().apply {
            setBinary(firefoxPath)
        }
    } ?: FirefoxOptions()

fun String?.toChromeOptions() =
    this?.let { chromePath ->
        ChromeOptions().apply {
            setBinary(chromePath)
        }
    } ?: ChromeOptions()

fun getDriver(options: Options): WebDriver =
    options.let { (browser, browserPath) ->
        when (browser) {
            Browser.FIREFOX -> firefox(browserPath.toFirefoxOptions())
            Browser.CHROME -> chrome(browserPath.toChromeOptions())
        }
    }

fun WebDriver.waitFor(
    locator: By,
    waitFn: (locator: By) -> ExpectedCondition<WebElement> = { ExpectedConditions.presenceOfElementLocated(it) }
): WebElement =
    WebDriverWait(this, Duration.ofSeconds(MAX_WAIT))
        .until(waitFn(locator))

interface Selectable {
    val id: String?
    val xpath: String?
    val elementName: String?
}

enum class Jibble(
    override val id: String? = null,
    override val xpath: String? = null,
    override val elementName: String? = null,
) : Selectable {
    GOOGLE_LOGIN(xpath = "/html/body/div[1]/div/div[1]/div[2]/div/div[2]/div/div/div/div/div[3]/div/button[2]"),
    CLOCK_IN_BUTTON(xpath = "/html/body/div[1]/div[1]/div[1]/header/div/div/div[2]/div/div[2]"),
    CLOCK_IN_FIELD(xpath = "/html/body/div[1]/div/div[1]/div[4]/div/div[2]/div[2]/main/div[2]/div[1]/div[1]/div/div/div/span/input"),
    CLOCK_IN_CONFIRM(xpath = "/html/body/div[1]/div/div[1]/div[4]/div/div[2]/div[2]/footer/div[1]/div/button[2]")
}

enum class Google(
    override val id: String? = null,
    override val xpath: String? = null,
    override val elementName: String? = null,
) : Selectable {
    EMAIL_TEXT_FIELD(id = "identifierId"),
    PASSWORD_TEXT_FIELD(xpath = "//*[@id=\"password\"]/div[1]/div/div[1]/input")
}

object From {
    fun selector(selector: Selectable): By =
        selector.id?.let(By::id)
            ?: selector.xpath?.let(By::xpath)
            ?: selector.elementName?.let(By::name) as By
}

fun WebDriver.login(loginInfo: LoginInfo) {
    waitFor(From.selector(Jibble.GOOGLE_LOGIN)) { ExpectedConditions.elementToBeClickable(it) }
        .click()

    waitFor(From.selector(Google.EMAIL_TEXT_FIELD))
        .sendKeys(loginInfo.email, Keys.ENTER)

    waitFor(From.selector(Google.PASSWORD_TEXT_FIELD)) { ExpectedConditions.elementToBeClickable(it) }
        .apply {
            click()
            sendKeys(loginInfo.password, Keys.ENTER)
        }
}

fun WebDriver.addTimeEntry(
    openDialogButton: Selectable,
    inputField: Selectable,
    confirmButton: Selectable,
    at: LocalTime
) {
    waitFor(From.selector(openDialogButton))
        .click()

    waitFor(From.selector(inputField))
        .click()

    "$at".split(":").let {
        it[0] to it[1]
        findElement(From.selector(inputField)).apply {
            sendKeys(Keys.SHIFT, Keys.TAB)
            sendKeys(it[0])
            sendKeys(Keys.TAB)
            sendKeys(it[1])
            sendKeys(Keys.TAB)
        }
    }

    findElement(From.selector(confirmButton))
        .click()
}

fun WebDriver.scheduleTimeEntry(
    openDialogButton: Selectable,
    inputField: Selectable,
    confirmButton: Selectable,
    at: LocalTime,
) {
    Timer()
        .schedule(object : TimerTask() {
            override fun run() {
                addTimeEntry(openDialogButton, inputField, confirmButton, at)
            }
        }, LocalTime.now().until(at, ChronoUnit.MILLIS))
}

data class LoginInfo(
    val email: String,
    val password: String,
)

data class TimeInterval(
    val from: LocalTime,
    val to: LocalTime,
)

data class Schedule(
    val base: TimeInterval,
    val breaks: Collection<TimeInterval>
)

data class JibbleConfig(
    val profile: LoginInfo,
    val schedule: Schedule
)

fun loadConfig() =
    jacksonObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }
        .readValue<JibbleConfig>(
            File(
                ClassLoader
                    .getSystemResource("jibbleConfig.json")
                    .toURI()
            )
                .readText()
        )

fun JibbleConfig.validate() =
    (listOf(schedule.base) + schedule.breaks)
        .sortedBy { s -> s.from }
        .zipWithNext()
        .fold(true) { acc, (i1, i2) ->
            i1.to <= i2.from && acc
        }
        .takeIf { it }
        ?.let { this }

enum class Browser {
    CHROME,
    FIREFOX
}

data class Options(
    val browser: Browser,
    val browserPath: String?
)

fun Array<String>.parseOptions() =
    with(ArgParser("Auto Jibble")) {
        val browser by option(
            ArgType.Choice<Browser>(),
            shortName = "b",
            fullName = "browser",
            description = "Browser to be used"
        )
            .default(Browser.FIREFOX)

        val browserPath by option(
            ArgType.String,
            shortName = "p",
            fullName = "browser-path",
            description = "Path to the browser executable"
        )

        parse(this@parseOptions)
        Options(browser, browserPath)
    }

fun main(args: Array<String>) {
    val (profile, schedule) = loadConfig()
    val driver = getDriver(args.parseOptions())

    driver.get("https://www.jibble.io/app")

    driver.login(profile)

    driver.addTimeEntry(
        Jibble.CLOCK_IN_BUTTON,
        Jibble.CLOCK_IN_FIELD,
        Jibble.CLOCK_IN_CONFIRM,
        schedule.base.from
    )

    driver.scheduleTimeEntry(
        Jibble.CLOCK_IN_BUTTON,
        Jibble.CLOCK_IN_FIELD,
        Jibble.CLOCK_IN_CONFIRM,
        schedule.base.to
    )

    schedule.breaks.forEach { (from, to) ->
        driver.scheduleTimeEntry(
            Jibble.CLOCK_IN_BUTTON,
            Jibble.CLOCK_IN_FIELD,
            Jibble.CLOCK_IN_CONFIRM,
            from
        )
        driver.scheduleTimeEntry(
            Jibble.CLOCK_IN_BUTTON,
            Jibble.CLOCK_IN_FIELD,
            Jibble.CLOCK_IN_CONFIRM,
            to
        )
    }
}
