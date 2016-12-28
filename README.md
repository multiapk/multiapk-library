ctrip framework library.
---
###hot to use
```
    compile 'com.mlibrary:mlibrarypatch:0.0.1'
```
```
public class MApplication extends Application {
    @Override
    public void onCreate() {
        if (!BuildConfig.solidMode)
            MultiDex.install(this);
        super.onCreate();
        if (BuildConfig.solidMode)
            MLibraryPatch.init(this);
    }
}
```
###examples
https://github.com/mlibrarys/MDynamicHome
---