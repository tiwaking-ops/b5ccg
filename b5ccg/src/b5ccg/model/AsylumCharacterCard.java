package b5ccg.model;

import b5ccg.model.enums.*;
import java.util.*;

/**
 * B5-0661: an ambassador copy placed in asylum by the Unconditional Surrender
 * rule (rulebook :819). Extends CharacterCard with a flag that blocks elevation
 * to the Inner Circle for the lifetime of the asylum copy.
 *
 * The asylum copy is a duplicate of the surrendering player's ambassador,
 * entered as a SUPPORTING character on the target's side, starting Clean
 * (no aftermath/enhancement attachments carried over). The printed stats are
 * identical to the original ambassador; only the asylum flag and the
 * isAmbassador=false distinction differ from the original.
 */
public class AsylumCharacterCard extends CharacterCard {
    private boolean inAsylum = true;

    /**
     * Creates an asylum duplicate of an existing ambassador character.
     * All printed stats are copied from the original; the asylum flag is set
     * so elevation to the Inner Circle is refused (B5-0661 rulebook :819).
     */
    public AsylumCharacterCard(String id, String title, String subtype,
                                Rarity rarity, Faction faction, CardSet cardSet,
                                String imageKey, String text,
                                int diplomacy, int intrigue, int psi, int leadership,
                                CharacterCard originalAmbassador) {
        super(id, title, subtype, rarity, faction, cardSet, imageKey, text,
              diplomacy, intrigue, psi, leadership, false); // not an ambassador
        this.inAsylum = true;
    }

    /** B5-0661: an asylum card may not be elevated to the Inner Circle. */
    public boolean isInAsylum() { return inAsylum; }
}
