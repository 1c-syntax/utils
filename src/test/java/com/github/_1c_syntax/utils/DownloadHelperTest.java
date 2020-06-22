package com.github._1c_syntax.utils;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHAsset;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class DownloadHelperTest {

  private final Path BASE_PATH = Path.of("build", "fixture");

  @Test
  public void test_getLatestRelease() {
    prepareFolder(BASE_PATH);
    var token = System.getenv("GITHUB_TOKEN");

    var ghAsset = DownloadHelper.getLatestRelease(token, DownloadHelper.SUPPLY_FOR_WIN, BASE_PATH);
    checkGHAsset(ghAsset);

    ghAsset = DownloadHelper.getLatestRelease(token, DownloadHelper.SUPPLY_FOR_LINUX, BASE_PATH);
    checkGHAsset(ghAsset);

    ghAsset = DownloadHelper.getLatestRelease(token, DownloadHelper.SUPPLY_FOR_MAC, BASE_PATH, true);
    checkGHAsset(ghAsset);
  }

  void checkGHAsset(Optional<GHAsset> ghAsset) {
    assertThat(ghAsset).isPresent();
    var file = Path.of(BASE_PATH.toString(), ghAsset.get().getName()).toFile();
    assertThat(file).exists();
  }


  private void prepareFolder(Path path) {
    path.toFile().mkdir();
  }

}
