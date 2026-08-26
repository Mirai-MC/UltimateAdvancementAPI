package com.fren_gor.ultimateAdvancementAPI.util;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Layout algorithms for advancement trees.
 */
public final class AdvancementLayout {

    private AdvancementLayout() {
        throw new UnsupportedOperationException("Utility class.");
    }

    /**
     * Applies the same tidy-tree layout used by the vanilla server.
     * <p>The layout uses the parent relationship and writes the resulting coordinates to
     * each display. It must be called before an advancement is first sent to a player.
     *
     * @param root The root advancement.
     * @param advancements The non-root advancements in the tree.
     */
    public static void applyVanilla(@NotNull RootAdvancement root,
                                    @NotNull Collection<? extends BaseAdvancement> advancements) {
        Preconditions.checkNotNull(root, "RootAdvancement is null.");
        Preconditions.checkNotNull(advancements, "Advancements are null.");

        Set<BaseAdvancement> declared = new HashSet<>(advancements);
        Map<Advancement, List<BaseAdvancement>> children = new HashMap<>();
        children.put(root, new ArrayList<>());
        for (BaseAdvancement advancement : advancements) {
            Preconditions.checkNotNull(advancement, "An advancement is null.");
            Advancement parent = Preconditions.checkNotNull(advancement.getParent(),
                    "Advancement %s has no parent.", advancement.getKey());
            Preconditions.checkArgument(parent == root || declared.contains(parent),
                    "Parent advancement %s is not included in the layout tree.", parent.getKey());
            children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(advancement);
            children.putIfAbsent(advancement, new ArrayList<>());
        }
        Comparator<BaseAdvancement> byKey = Comparator.comparing(advancement -> advancement.getKey().toString());
        children.values().forEach(list -> list.sort(byKey));

        Set<Advancement> visited = new HashSet<>();
        validateGraph(root, children, new HashSet<>(), visited);
        Preconditions.checkArgument(visited.containsAll(declared),
                "Advancement layout contains a branch disconnected from the root.");
        Position rootPosition = new Position(root, null, null, 1, 0, children);
        rootPosition.firstWalk();
        float min = rootPosition.secondWalk(0, 0, rootPosition.y);
        if (min < 0) {
            rootPosition.thirdWalk(-min);
        }
        rootPosition.finalizePosition();
    }

    private static void validateGraph(Advancement advancement,
                                      Map<Advancement, List<BaseAdvancement>> children,
                                      Set<Advancement> visiting,
                                      Set<Advancement> visited) {
        if (visited.contains(advancement)) {
            return;
        }
        if (!visiting.add(advancement)) {
            throw new IllegalArgumentException("Advancement graph contains a cycle at " + advancement.getKey() + '.');
        }
        for (BaseAdvancement child : children.getOrDefault(advancement, List.of())) {
            validateGraph(child, children, visiting, visited);
        }
        visiting.remove(advancement);
        visited.add(advancement);
    }

    private static final class Position {
        private final Advancement advancement;
        private final Position parent;
        private final Position previousSibling;
        private final int childIndex;
        private final List<Position> children = new ArrayList<>();
        private Position ancestor;
        private Position thread;
        private int x;
        private float y = -1;
        private float mod;
        private float change;
        private float shift;

        private Position(Advancement advancement, Position parent, Position previousSibling, int childIndex, int depth,
                         Map<Advancement, List<BaseAdvancement>> childrenByParent) {
            this.advancement = advancement;
            this.parent = parent;
            this.previousSibling = previousSibling;
            this.childIndex = childIndex;
            this.ancestor = this;
            this.x = depth;

            Position previous = null;
            for (BaseAdvancement child : childrenByParent.getOrDefault(advancement, List.of())) {
                Position childPosition = new Position(child, this, previous, this.children.size() + 1,
                        depth + 1, childrenByParent);
                this.children.add(childPosition);
                previous = childPosition;
            }
        }

        private void firstWalk() {
            if (children.isEmpty()) {
                y = previousSibling == null ? 0 : previousSibling.y + 1;
                return;
            }

            Position defaultAncestor = null;
            for (Position child : children) {
                child.firstWalk();
                defaultAncestor = child.apportion(defaultAncestor == null ? child : defaultAncestor);
            }
            executeShifts();
            float midpoint = (children.get(0).y + children.get(children.size() - 1).y) / 2;
            if (previousSibling != null) {
                y = previousSibling.y + 1;
                mod = y - midpoint;
            } else {
                y = midpoint;
            }
        }

        private float secondWalk(float modSum, int depth, float min) {
            y += modSum;
            x = depth;
            min = Math.min(min, y);
            for (Position child : children) {
                min = child.secondWalk(modSum + mod, depth + 1, min);
            }
            return min;
        }

        private void thirdWalk(float offset) {
            y += offset;
            for (Position child : children) {
                child.thirdWalk(offset);
            }
        }

        private void executeShifts() {
            float shiftValue = 0;
            float changeValue = 0;
            for (int i = children.size() - 1; i >= 0; i--) {
                Position child = children.get(i);
                child.y += shiftValue;
                child.mod += shiftValue;
                changeValue += child.change;
                shiftValue += child.shift + changeValue;
            }
        }

        private Position previousOrThread() {
            return thread != null ? thread : (children.isEmpty() ? null : children.get(0));
        }

        private Position nextOrThread() {
            return thread != null ? thread : (children.isEmpty() ? null : children.get(children.size() - 1));
        }

        private Position apportion(Position defaultAncestor) {
            if (previousSibling == null) {
                return defaultAncestor;
            }

            Position vir = this;
            Position vor = this;
            Position vil = previousSibling;
            Position vol = parent.children.get(0);
            float sir = mod;
            float sor = mod;
            float sil = vil.mod;
            float sol = vol.mod;

            while (vil.nextOrThread() != null && vir.previousOrThread() != null) {
                vil = vil.nextOrThread();
                vir = vir.previousOrThread();
                vol = vol.previousOrThread();
                vor = vor.nextOrThread();
                vor.ancestor = this;
                float requiredShift = vil.y + sil - (vir.y + sir) + 1;
                if (requiredShift > 0) {
                    vil.getAncestor(this, defaultAncestor).moveSubtree(this, requiredShift);
                    sir += requiredShift;
                    sor += requiredShift;
                }
                sil += vil.mod;
                sir += vir.mod;
                sol += vol.mod;
                sor += vor.mod;
            }

            if (vil.nextOrThread() != null && vor.nextOrThread() == null) {
                vor.thread = vil.nextOrThread();
                vor.mod += sil - sor;
            } else {
                if (vir.previousOrThread() != null && vol.previousOrThread() == null) {
                    vol.thread = vir.previousOrThread();
                    vol.mod += sir - sol;
                }
                defaultAncestor = this;
            }
            return defaultAncestor;
        }

        private void moveSubtree(Position right, float requiredShift) {
            float subtrees = right.childIndex - childIndex;
            if (subtrees != 0) {
                right.change -= requiredShift / subtrees;
                change += requiredShift / subtrees;
            }
            right.shift += requiredShift;
            right.y += requiredShift;
            right.mod += requiredShift;
        }

        private Position getAncestor(Position other, Position defaultAncestor) {
            return ancestor != null && other.parent.children.contains(ancestor) ? ancestor : defaultAncestor;
        }

        private void finalizePosition() {
            advancement.getDisplay().setLocation(x, y);
            for (Position child : children) {
                child.finalizePosition();
            }
        }
    }
}
