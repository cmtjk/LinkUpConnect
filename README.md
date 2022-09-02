# LLU Client

Displays your current blood glucose level fetched from LibreView as notification: Status bar, lock screen, smartwatch (partially).

Special thanks to [Nechoj](https://insulinclub.de/core/index.php?user/46518-nechoj/)

## Requirements
- LibreView Account (invite yourself in FreeStyle Libre app: https://www.librelinkup.com/articles/getting-started)

Note: It seems you have to install and log into the LibreLinkUp app once and accept the invitation but you can uninstall it afterwards. Otherwise no data is sent.

Want mmol/l? Enable debug and tell me how your payload looks like. Then I can implement it.

## How it works
The application directly queries the LibreView API every 60 seconds (default) to get current blood glucose levels. Hence it requires an internet connection to work.

The notification will show your current blood glucose level, the trend arrow, the time passed since the last measurement and its timestamp.

![](img/notification_android9.png)

If the time passed is more than 5 minutes it'll show an exclamation mark.

![](img/notification_outdated_android9.png)

The notification is send to your smartwatch, too, but doesn't update. That's something I might working on. Help welcome.

![](img/huaweiwatchgt3.jpg)

## Why it sometimes doesn't work
This is an very opinionated implementation with basic Android knowledge and it's only really tested on my Honor Play and Huawei Watch GT 3.

This application has bugs. Expect crashes. But I use it on a daily basis and it works sufficiently. If there's something you want to be implemented or improved create an issue, PR, fork, or contact me.

I'll not implement more sophisticated features but I'm open to increase compatibility with older and newer devices and fix bugs of course. But expect to help me with information.

If something doesn't work remember to enable debug logging. Unfortunately, it's not persistent since I debug directly in Android Studio. So If you application crashes there's not much information. Maybe I'll improve this in the future.


## Known issues
- Since the authorization token is saved for its life time you currently have to delete the application data and cache when you want to log in with another user.
- Same goes for the connection ID which is stored forever. If you change your device or maybe even if you reinstall your FreeStyle Libre app you have to delete the application data and cache and log in again.
- If you have more than one blood glucose meter only the data of the first one provided by the API is shown. This is sufficient for me since I only use the FreeStyle Libre 3 app.
- And maybe more for users with a different setup. Contact me. Maybe I can help.