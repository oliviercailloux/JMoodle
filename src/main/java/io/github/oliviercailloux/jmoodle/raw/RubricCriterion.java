package io.github.oliviercailloux.jmoodle.raw;

import java.util.Set;

public record RubricCriterion (int criterionid, Set<RubricFilling> fillings) {

}
