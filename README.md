# Auto Jibble

## Why ?
For the mighty mr. Magi that always forgets to mark his schedules.

## How ?
Kotlin + Java selenium connector + Java webdriver manager

### SET-UP
- create a `jibbleConfig.json` file in the `src/main/resources` folder of the project.  
Example of the file:

```json
{
  "profile": {
    "email": "your.email@something.com",
    "password": "mightylord69",
    "schedule": {
      "base": {
        "from": "08:00",
        "to": "17:30"
      },
      "breaks": []
    }
  }
} 
```
