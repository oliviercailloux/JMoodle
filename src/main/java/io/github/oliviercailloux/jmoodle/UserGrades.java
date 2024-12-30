package io.github.oliviercailloux.jmoodle;

import com.google.common.collect.ImmutableMap;

public record UserGrades (UserId userId, ImmutableMap<Integer, Double> grades) {

}
