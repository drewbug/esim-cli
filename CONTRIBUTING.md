# esim-cli

```
sudo apt install android-sdk
```

## Debug

```
ANDROID_HOME=/usr/lib/android-sdk ./gradlew installDebug
```

## Release

```
ANDROID_HOME=/usr/lib/android-sdk ./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=$KEYFILE \
  -Pandroid.injected.signing.store.password=$STORE_PASSWORD \
  -Pandroid.injected.signing.key.alias=$KEY_ALIAS \
  -Pandroid.injected.signing.key.password=$KEY_PASSWORD
```


