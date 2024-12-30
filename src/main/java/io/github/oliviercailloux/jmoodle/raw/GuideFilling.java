package io.github.oliviercailloux.jmoodle.raw;

import java.util.Optional;

public record GuideFilling (int criterionid, Optional<Integer> levelid, Optional<String> remark,
    Optional<Integer> remarkformat, double score) {

}
