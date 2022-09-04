# LinkUpConnect

[Download ⬇️](https://github.com/cmtjk/LinkUpConnect/releases)

Displays your current blood glucose level fetched from LibreView as notification: status bar, lock screen, smartwatch (partially).

## Attributions
- Special thanks to [Nechoj](https://insulinclub.de/core/index.php?user/46518-nechoj/) for the inspiration
- ![](img/blood_sugar_icon.png)[Diabetes icons created by Freepik - Flaticon](https://www.flaticon.com/free-icons/diabetes "diabetes icons")

## Requirements
- LibreView Account (invite yourself in FreeStyle Libre app: https://www.librelinkup.com/articles/getting-started)

Note: It seems you have to install and log into the LibreLinkUp app once and accept the invitation but you can uninstall it afterwards. Otherwise no data is sent.

Want mmol/l? Enable debug and tell me how your payload looks like. Then I can implement it.

## How it works
The application directly queries the LibreView API every 60 seconds (default) to get current blood glucose levels. Hence it requires an internet connection to work.

![](img/layout_view.png)

The notification will show your current blood glucose level, the trend arrow, the time passed since the last measurement and its timestamp.

![](img/notification_android9.png)

If the time passed is more than 5 minutes it'll show an exclamation mark.

![](img/notification_outdated_android9.png)

The notification is send to your smartwatch, too, but doesn't update. I think I can't change it because of device specific notification handling. WearOS watches might work but I don't have one.

![](img/huaweiwatchgt3.jpg)

## Why it sometimes doesn't work
This is an very opinionated implementation with basic Android knowledge and it's only really tested on my Honor Play and Huawei Watch GT 3.

This application has bugs. Expect crashes. But I use it on a daily basis and it works sufficiently. If there's something you want to be implemented or improved create an issue, PR, fork, or contact me.

I'll not implement more sophisticated features by now but I'm open to increase compatibility with older and newer devices and fix bugs of course. But expect to help me with information.

If something doesn't work remember to enable debug logging. Unfortunately, it's not persistent since I debug directly in Android Studio. So If you application crashes there's not much information. Maybe I'll improve this in the future.


## Known issues
- If your LibreView account is connected to more than one patient only one is shown. This is sufficient for me since I only need notifications about myself.
- And maybe more for users with a different setup. Contact me. Maybe I can help.