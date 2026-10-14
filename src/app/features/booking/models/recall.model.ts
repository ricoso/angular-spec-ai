/**
 * REQ-041: the recall campaign ("Rückrufaktion") lives in its own SoftNet group with exactly one
 * operation per branch. Shared between the store (deep-link activation + service load) and the
 * location cards (the blue "Rückrufaktion" shortcut card). Kept separate so the presentational
 * card component does not import the store.
 */
export const RECALL_GROUP_ID = '6ac7939ba4c0acee699905a8';
