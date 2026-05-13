package com.samarth.parties;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable group of players led by exactly one player. Members are stored in
 * insertion order — when the leader leaves, the longest-joined remaining
 * member becomes the new leader.
 */
public final class Party {
    private final UUID id;
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final long createdAtMillis;

    public Party(UUID leader) {
        this.id = UUID.randomUUID();
        this.leader = leader;
        this.members.add(leader);
        this.createdAtMillis = System.currentTimeMillis();
    }

    public UUID id() { return id; }
    public UUID leader() { return leader; }
    public Set<UUID> members() { return Collections.unmodifiableSet(members); }
    public int size() { return members.size(); }
    public long createdAtMillis() { return createdAtMillis; }

    public boolean isLeader(UUID id) { return leader.equals(id); }
    public boolean contains(UUID id) { return members.contains(id); }

    /** Add a member. Returns true if the member was new to the party. */
    public boolean addMember(UUID id) {
        return members.add(id);
    }

    /**
     * Remove a member. Returns true if the member was present.
     * If the removed member was the leader and other members remain, the
     * next-longest-joined member is promoted.
     */
    public boolean removeMember(UUID id) {
        boolean removed = members.remove(id);
        if (removed && id.equals(leader) && !members.isEmpty()) {
            leader = members.iterator().next();
        }
        return removed;
    }

    /** Promote an existing member to leader. No-op if the target isn't a member. */
    public boolean promote(UUID newLeader) {
        if (!members.contains(newLeader)) return false;
        this.leader = newLeader;
        // Re-order so the new leader is first (preserves "longest-joined" semantics
        // for future auto-promotions).
        Set<UUID> reordered = new LinkedHashSet<>();
        reordered.add(newLeader);
        for (UUID m : members) if (!m.equals(newLeader)) reordered.add(m);
        members.clear();
        members.addAll(reordered);
        return true;
    }
}
