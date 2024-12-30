package io.github.oliviercailloux.jmoodle.raw;

import java.util.Set;

public record GuideCriterion (int criterionid, Set<GuideFilling> fillings) {

}
