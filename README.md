esim-cli
========

[![](./logo.png)](#)

1. ```shell
   adb install esim-cli.apk
   ```

2. ```shell
   adb shell "content read --uri content://esim-cli | sh"
   ```

## Non-interactive Scripting Mode

To send a single SMS in a fully scripted environment, pass `-s SEND_SMS` with addressee and body like so:

```shell
adb shell "content read --uri content://esim-cli | sh -s SEND_SMS '+15558675309' 'This is not a drill 🪛'"
```
