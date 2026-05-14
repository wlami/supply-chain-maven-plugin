package com.wlami.supplychain.mojo;

import com.wlami.supplychain.source.ReleaseDateCache;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mojo(name = "refresh-cache", threadSafe = true)
public class RefreshCacheMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        Path cacheRoot = Paths.get(System.getProperty("user.home"),
            ".m2", "repository", ".supply-chain-cache");
        ReleaseDateCache cache = new ReleaseDateCache(cacheRoot.resolve("release-dates.json"));
        cache.clear();
        cache.flush();
        getLog().info("Cleared release-date cache at " + cacheRoot);
    }
}
