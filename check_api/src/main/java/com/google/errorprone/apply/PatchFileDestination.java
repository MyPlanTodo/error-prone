/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.apply;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import difflib.DiffUtils;
import difflib.Patch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A {@link FileDestination} that writes a unix-patch file to {@code rootPath} containing the
 * suggested changes.
 */
public final class PatchFileDestination implements FileDestination {

  // TODO(glorioso): This won't work for Windows, although getting unix patch on Windows is
  // a bit funky.
  private static final Splitter LINE_SPLITTER = Splitter.on('\n');

  private final Path baseDir;
  private final Path rootPath;
  // Path -> Unified Diff String, sorted by path
  private final Map<String, String> diffByFile = new TreeMap<>();

  public PatchFileDestination(Path baseDir, Path rootPath) {
    this.baseDir = baseDir;
    this.rootPath = rootPath;
  }

  @Override
  public void writeFile(SourceFile update) throws IOException {
    Path originalFilePath = rootPath.resolve(update.getPath());
    String oldSource = new String(Files.readAllBytes(originalFilePath), UTF_8);
    String newSource = update.getSourceText();
    if (!oldSource.equals(newSource)) {
      List<String> originalLines = LINE_SPLITTER.splitToList(oldSource);

      Patch<String> diff = DiffUtils.diff(originalLines, LINE_SPLITTER.splitToList(newSource));
      String relativePath = relativize(update);
      List<String> unifiedDiff =
          DiffUtils.generateUnifiedDiff(relativePath, relativePath, originalLines, diff, 2);

      String diffString = Joiner.on("\n").join(unifiedDiff);
      diffByFile.put(originalFilePath.toString(), diffString);
    }
  }

  private String relativize(SourceFile update) {
    return baseDir.relativize(rootPath.resolve(update.getPath())).toString();
  }

  public String patchFile() {
    return diffByFile.values().stream().collect(Collectors.joining("\n"));
  }

  @Override
  public void flush() throws IOException {}
}
