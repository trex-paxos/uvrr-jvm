// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core;

import com.github.trex_paxos.core.ids.*;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdsContractTest {

    @Test
    void successorsDoNotWrap() {
        assertThat(new View(0xFFFF_FFFFL).next()).isEmpty();
        assertThat(new Era(0xFFFF_FFFFL).next()).isEmpty();
        assertThat(new Slot(Long.MAX_VALUE).next()).isEmpty();
        assertThat(new Slot(0).prev()).isEmpty();

        assertThat(new View(0).next()).contains(new View(1));
        assertThat(new Era(7).next()).contains(new Era(8));
        assertThat(new Slot(0).next()).contains(new Slot(1));
        assertThat(new Slot(1).prev()).contains(new Slot(0));

        assertThat(View.INITIAL).isEqualTo(new View(0));
        assertThat(Era.INITIAL).isEqualTo(new Era(0));
        assertThat(Slot.NONE).isEqualTo(new Slot(0));
    }

    @Test
    void ballotSuccessorsDoNotWrap() {
        Ballot atViewMax = new Ballot(new Era(3), new View(0xFFFF_FFFFL));
        assertThat(atViewMax.nextInEra()).isEmpty();
        assertThat(atViewMax.nextInNextEra()).isEmpty();

        Ballot atEraMax = new Ballot(new Era(0xFFFF_FFFFL), new View(9));
        assertThat(atEraMax.nextInEra()).contains(new Ballot(new Era(0xFFFF_FFFFL), new View(10)));
        assertThat(atEraMax.nextInNextEra()).isEmpty();

        Ballot ordinary = new Ballot(new Era(4), new View(11));
        assertThat(ordinary.nextInEra()).contains(new Ballot(new Era(4), new View(12)));
        assertThat(ordinary.nextInNextEra()).contains(new Ballot(new Era(5), new View(12)));
    }

    @Test
    void legalSuccessorTruthTable() {
        long[] deltas = {-1, 0, 1, 2};
        Ballot base = new Ballot(new Era(10), new View(20));

        for (long eraDelta : deltas) {
            for (long viewDelta : deltas) {
                Ballot next = new Ballot(new Era(10 + eraDelta), new View(20 + viewDelta));
                boolean expected = viewDelta > 0 && (eraDelta == 0 || eraDelta == 1);
                assertThat(base.isLegalSuccessor(next))
                        .as("era delta %d, view delta %d", eraDelta, viewDelta)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void legalSuccessorRejectIdentityAndRegression() {
        Ballot v = new Ballot(new Era(2), new View(2));
        assertThat(v.isLegalSuccessor(v)).isFalse();
        assertThat(v.isLegalSuccessor(new Ballot(new Era(2), new View(3)))).isTrue();
        assertThat(v.isLegalSuccessor(new Ballot(new Era(3), new View(3)))).isTrue();
        assertThat(v.isLegalSuccessor(new Ballot(new Era(1), new View(3)))).isFalse();
        assertThat(v.isLegalSuccessor(new Ballot(new Era(4), new View(3)))).isFalse();
    }

    @Test
    void constructorsAgreeWithLegality() {
        for (long era = 0; era < 8; era++) {
            for (long view = 0; view < 8; view++) {
                Ballot v = new Ballot(new Era(era), new View(view));
                Ballot inEra = v.nextInEra().orElseThrow();
                Ballot nextEra = v.nextInNextEra().orElseThrow();
                assertThat(v.isLegalSuccessor(inEra)).isTrue();
                assertThat(v.isLegalSuccessor(nextEra)).isTrue();
            }
        }
    }

    @Test
    void legalSuccessorAtEraExhaustion() {
        Ballot base = new Ballot(new Era(0xFFFF_FFFFL), new View(7));
        assertThat(base.isLegalSuccessor(new Ballot(new Era(0xFFFF_FFFFL), new View(8)))).isTrue();
        assertThat(base.isLegalSuccessor(base)).isFalse();
        assertThat(base.isLegalSuccessor(new Ballot(new Era(0xFFFF_FFFFL), new View(6)))).isFalse();
        assertThat(base.isLegalSuccessor(new Ballot(new Era(0xFFFF_FFFFL - 1), new View(8)))).isFalse();

        assertThat(base.nextInNextEra()).isEmpty();
        assertThat(base.nextInEra()).contains(new Ballot(new Era(0xFFFF_FFFFL), new View(8)));
    }

    @Provide
    Arbitrary<List<Ballot>> legalHistories() {
        return Arbitraries.integers().between(1, 20).flatMap(len ->
                Arbitraries.integers().between(1, 3).list().ofSize(len).flatMap(viewSteps ->
                        Arbitraries.integers().between(0, 1).list().ofSize(len).map(eraSteps -> {
                            List<Ballot> out = new ArrayList<>();
                            long era = 0;
                            long view = 0;
                            out.add(new Ballot(new Era(era), new View(view)));
                            for (int i = 0; i < len; i++) {
                                view += viewSteps.get(i);
                                era += eraSteps.get(i);
                                out.add(new Ballot(new Era(era), new View(view)));
                            }
                            return out;
                        })
                )
        );
    }

    @Property
    void orderingsAgreeOnLegalPairs(@ForAll("legalHistories") List<Ballot> history) {
        for (Ballot a : history) {
            for (Ballot b : history) {
                int byEraThenView = a.compareTo(b);
                int byViewThenEra = a.view().compareTo(b.view()) != 0
                        ? a.view().compareTo(b.view())
                        : a.era().compareTo(b.era());
                assertThat(Integer.signum(byEraThenView))
                        .as("orderings disagree on %s vs %s", a, b)
                        .isEqualTo(Integer.signum(byViewThenEra));
            }
        }
    }

    @Test
    void nextViewSelectingIsLeastAndCorrect() {
        for (long members = 1; members <= 8; members++) {
            for (long index = 0; index < members; index++) {
                for (long current = 0; current < 64; current++) {
                    View got = Ids.nextViewSelecting(new View(current), index, members)
                            .orElseThrow();
                    assertThat(got.value()).isGreaterThan(current);
                    assertThat(got.value() % members).isEqualTo(index);
                    for (long skipped = current + 1; skipped < got.value(); skipped++) {
                        assertThat(skipped % members).isNotEqualTo(index);
                    }
                }
            }
        }
    }

    @Test
    void nextViewSelectingRejectsDegenerateArguments() {
        assertThat(Ids.nextViewSelecting(new View(0), 0, 0)).isEmpty();
        assertThat(Ids.nextViewSelecting(new View(7), 3, 0)).isEmpty();
        for (long members = 1; members <= 8; members++) {
            assertThat(Ids.nextViewSelecting(new View(5), members, members)).isEmpty();
            assertThat(Ids.nextViewSelecting(new View(5), members + 1, members)).isEmpty();
            assertThat(Ids.nextViewSelecting(new View(5), 0xFFFF_FFFFL, members)).isEmpty();
        }
    }

    @Test
    void nextViewSelectingDoesNotWrap() {
        assertThat(Ids.nextViewSelecting(new View(0xFFFF_FFFFL), 0, 3)).isEmpty();
        assertThat(Ids.nextViewSelecting(new View(0xFFFF_FFFFL), 1, 3)).isEmpty();
        assertThat(Ids.nextViewSelecting(new View(0xFFFF_FFFFL), 2, 3)).isEmpty();

        assertThat(Ids.nextViewSelecting(new View(0xFFFF_FFFFL - 3), 0, 3))
                .contains(new View(0xFFFF_FFFFL));
        assertThat(Ids.nextViewSelecting(new View(0xFFFF_FFFFL - 1), 1, 3)).isEmpty();

        assertThat(Ids.nextViewSelecting(new View(0xFFFF_FFFFL), 0, 1)).isEmpty();
        assertThat(Ids.nextViewSelecting(new View(0xFFFF_FFFFL - 1), 0, 1))
                .contains(new View(0xFFFF_FFFFL));
    }

    @Test
    void slotDistanceIsExactOrAbsent() {
        assertThat(new Slot(4).distanceTo(new Slot(4))).contains(0L);
        assertThat(new Slot(4).distanceTo(new Slot(9))).contains(5L);
        assertThat(new Slot(4).distanceTo(new Slot(3))).isEmpty();
        assertThat(new Slot(0).distanceTo(new Slot(Long.MAX_VALUE))).contains(Long.MAX_VALUE);
        assertThat(new Slot(Long.MAX_VALUE).distanceTo(new Slot(0))).isEmpty();
        assertThat(new Slot(Long.MAX_VALUE).distanceTo(new Slot(Long.MAX_VALUE))).contains(0L);

        for (long from = 0; from < 16; from++) {
            for (long to = 0; to < 16; to++) {
                Optional<Long> got = new Slot(from).distanceTo(new Slot(to));
                if (to >= from) {
                    assertThat(got).contains(to - from);
                } else {
                    assertThat(got).isEmpty();
                }
            }
        }
    }

    @Test
    void faultVariantsAreExhaustiveAndSticky() {
        Fault[] all = Fault.values();
        assertThat(all).containsExactly(
                Fault.ILLEGAL_TRANSITION,
                Fault.INDETERMINATE_PERSISTENCE,
                Fault.QUORUM_OBLIGATION,
                Fault.PROGRESS_JOURNAL_DIVERGENCE,
                Fault.HOST_DECLARED
        );

        for (Fault fault : all) {
            assertThat(fault.isSticky()).isTrue();
        }
    }
}
