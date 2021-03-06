// Copyright 2015 Eivind Vegsundvåg
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package ninja.eivind.hotsreplayuploader.files;

import ninja.eivind.hotsreplayuploader.utils.StormHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Singleton
public class AccountDirectoryWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(AccountDirectoryWatcher.class);
    private final Set<File> watchDirectories;
    private StormHandler stormHandler;
    private Set<WatchHandler> watchHandlers = new HashSet<>();

    @Inject
    public AccountDirectoryWatcher(StormHandler stormHandler) {
        this.stormHandler = stormHandler;
        watchDirectories = new HashSet<>(stormHandler.getHotSAccountDirectories());
        beginWatch();
    }

    public void beginWatch() {
        LOG.info("Initiating watch against directories:");
        watchDirectories.stream().map(file -> Paths.get(file.toString())).forEach(path -> {
            try {
                LOG.info("\t" + path);
                WatchHandler watchHandler = new WatchHandler(stormHandler, path);
                watchHandlers.add(watchHandler);
                new Thread(watchHandler).start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        LOG.info("Watcher initiated.");
    }

    public Stream<File> getAllFiles() {
        return watchDirectories.stream();
    }

    public void addFileListener(FileListener fileListener) {
        LOG.info("Adding listener " + fileListener.getClass());
        for (final WatchHandler watchHandler : watchHandlers) {
            watchHandler.addListener(fileListener);
        }
    }
}
