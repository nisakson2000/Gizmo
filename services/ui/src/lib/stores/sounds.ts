import { persistedWritable } from './persisted';

export const soundsEnabled = persistedWritable<boolean>('gizmo:sounds', false);
export const bootAnimationsEnabled = persistedWritable<boolean>('gizmo:bootAnimations', true);
