package io.github.oliviercailloux.jmoodle.raw;

import java.net.URI;

public class MoodleTestHelper {
  public static Moodle dumping(URI uri) {
    final Moodle moodle = Moodle.instance(uri);
    moodle.dump = true;
    return moodle;
  }
}
