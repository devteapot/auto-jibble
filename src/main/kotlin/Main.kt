import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.*
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.*

const val MAX_WAIT = 30L

fun firefox(options: FirefoxOptions = FirefoxOptions()): WebDriver {
    WebDriverManager.firefoxdriver().setup()
    return FirefoxDriver(options)
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
): Selectable {
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

    findElement(From.selector(Google.EMAIL_TEXT_FIELD))
        .sendKeys(loginInfo.email, Keys.ENTER)

    waitFor(From.selector(Google.PASSWORD_TEXT_FIELD)) { ExpectedConditions.elementToBeClickable(it) }
        .click()

    findElement(From.selector(Google.PASSWORD_TEXT_FIELD))
        .sendKeys(loginInfo.password, Keys.ENTER)
}


fun WebDriver.baseEntry(interval: TimeInterval) {
    waitFor(From.selector(Jibble.CLOCK_IN_BUTTON))
        .click()

    waitFor(From.selector(Jibble.CLOCK_IN_FIELD))
        .click()

    "${interval.start}".split(":").let {
        it[0] to it[1]
        findElement(From.selector(Jibble.CLOCK_IN_FIELD)).apply {
            sendKeys(Keys.SHIFT, Keys.TAB)
            sendKeys(it[0])
            sendKeys(Keys.TAB)
            sendKeys(it[1])
            sendKeys(Keys.TAB)
        }
    }

    findElement(From.selector(Jibble.CLOCK_IN_CONFIRM))
        .click()
}

data class LoginInfo (
    val email: String,
    val password: String,
)

data class TimeInterval(
    val start: LocalTime,
    val end: LocalTime,
)

data class Schedule (
    val base: TimeInterval,
    val breaks: Collection<TimeInterval>? = null
)

// This may be useful sometime in the future if i need to execure js
//    with(this as JavascriptExecutor) {
//        executeScript("arguments[0].value='${interval}';", element)
//        executeScript("console.log(arguments[0]);", element)
//    }

fun main(args: Array<String>) {

    val schedule = Schedule(
        TimeInterval(
            LocalTime.of(8, 0),
            LocalTime.of(21, 47)
        )
    )

    val driver = firefox()

    driver.get("https://www.jibble.io/app")

    driver.login(loginInfo)
    driver.baseEntry(schedule.base)

    LocalTime.now().let { now ->
        Timer()
            .schedule(object : TimerTask() {
                override fun run() {
                    println("POLLOOOOO")
                }
            }, now.until(schedule.base.end, ChronoUnit.MILLIS))

        schedule.breaks?.forEach { (start, end) ->
            Timer()
                .schedule(object : TimerTask() {
                    override fun run() {
                        println("POLLOOOOO")
                    }
                }, now.until(start, ChronoUnit.MILLIS))

            Timer()
                .schedule(object : TimerTask() {
                    override fun run() {
                        println("POLLOOOOO")
                    }
                }, now.until(end, ChronoUnit.MILLIS))
        }
    }

//    driver.quit()
}
