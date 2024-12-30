package io.github.oliviercailloux.jmoodle.raw;

import java.util.Optional;

public record RubricFilling (int criterionid, Optional<Integer> levelid, Optional<String> remark,
    Optional<Integer> remarkformat) {

}
