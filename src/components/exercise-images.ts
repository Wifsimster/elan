export type ExerciseImagePair = readonly [number, number];

const IMAGES: Record<string, ExerciseImagePair> = {
  'goblet-squat': [
    require('../../assets/images/exercises/goblet-squat-0.jpg'),
    require('../../assets/images/exercises/goblet-squat-1.jpg'),
  ],
  'dumbbell-bench-press': [
    require('../../assets/images/exercises/dumbbell-bench-press-0.jpg'),
    require('../../assets/images/exercises/dumbbell-bench-press-1.jpg'),
  ],
  'one-arm-dumbbell-row': [
    require('../../assets/images/exercises/one-arm-dumbbell-row-0.jpg'),
    require('../../assets/images/exercises/one-arm-dumbbell-row-1.jpg'),
  ],
  'dumbbell-lunges': [
    require('../../assets/images/exercises/dumbbell-lunges-0.jpg'),
    require('../../assets/images/exercises/dumbbell-lunges-1.jpg'),
  ],
  plank: [
    require('../../assets/images/exercises/plank-0.jpg'),
    require('../../assets/images/exercises/plank-1.jpg'),
  ],
  'dumbbell-romanian-deadlift': [
    require('../../assets/images/exercises/dumbbell-romanian-deadlift-0.jpg'),
    require('../../assets/images/exercises/dumbbell-romanian-deadlift-1.jpg'),
  ],
  'standing-dumbbell-press': [
    require('../../assets/images/exercises/standing-dumbbell-press-0.jpg'),
    require('../../assets/images/exercises/standing-dumbbell-press-1.jpg'),
  ],
  'bent-over-two-dumbbell-row': [
    require('../../assets/images/exercises/bent-over-two-dumbbell-row-0.jpg'),
    require('../../assets/images/exercises/bent-over-two-dumbbell-row-1.jpg'),
  ],
  'bulgarian-split-squat': [
    require('../../assets/images/exercises/bulgarian-split-squat-0.jpg'),
    require('../../assets/images/exercises/bulgarian-split-squat-1.jpg'),
  ],
  'side-plank': [
    require('../../assets/images/exercises/side-plank-0.jpg'),
    require('../../assets/images/exercises/side-plank-1.jpg'),
  ],
};

export function exerciseImages(key: string | undefined): ExerciseImagePair | null {
  if (!key) return null;
  return IMAGES[key] ?? null;
}
