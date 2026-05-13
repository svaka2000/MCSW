package com.samarth.parties;

import java.util.Collection;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Public API exposed via Bukkit's ServicesManager. PvPTLDuels soft-depends on
 * PvPTLParties and uses this for team-vs-team match setup.
 */
public interface PartyService {

    /** Create a new party with {@code leader} as the sole member. Returns null if they're already in a party. */
    @Nullable Party create(Player leader);

    @Nullable Party partyOf(UUID playerId);

    /** Send an invite to a player. Returns true on success (false if validation fails). */
    boolean invite(Player from, Player to);

    /** Accept a pending invite. Returns the joined party, or null if no pending invite. */
    @Nullable Party accept(Player target);

    /** Decline a pending invite. Returns true if there was one to decline. */
    boolean deny(Player target);

    /** Leave the current party. Returns true on success. */
    boolean leave(Player p);

    /** Disband the leader's party. Returns true if the caller was a leader. */
    boolean disband(Player leader);

    /** Kick a member. Returns true if the caller is the leader and the target was a member. */
    boolean kick(Player leader, Player target);

    /** Promote a member to leader. Returns true if successful. */
    boolean promote(Player leader, Player newLeader);

    /** Broadcast a chat message to every party member (used by /p). */
    void partyChat(Player from, String message);

    /** All currently active parties. */
    Collection<Party> allParties();

    boolean hasPendingInvite(UUID target);

    /** UUID of the player who most recently invited {@code target}, or null. */
    @Nullable UUID inviterOf(UUID target);
}
