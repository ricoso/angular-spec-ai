import { categoryIconClass } from './category-icon.util';

describe('categoryIconClass', () => {
  it.each([
    [0, 'bi-tools'],
    [1, 'bi-clipboard-check'],
    [2, 'bi-droplet-half'],
    [3, 'bi-record-circle'],
    [4, 'bi-check2-circle'],
    [5, 'bi-search'],
    [6, 'bi-car-front'],
    [99, 'bi-arrow-left-right'],
    [42, 'bi-tools']
  ])('rank %i → %s', (rank, icon) => {
    expect(categoryIconClass({ id: 't', name: 'X', rank })).toBe(icon);
  });

  it('falls back to bi-tools without a category', () => {
    expect(categoryIconClass(null)).toBe('bi-tools');
    expect(categoryIconClass(undefined)).toBe('bi-tools');
  });

  it('keeps the pick-up & delivery icon regardless of rank', () => {
    expect(categoryIconClass({ id: 'virtual-pickup-delivery', name: 'X', rank: 0 })).toBe('bi-arrow-left-right');
    expect(categoryIconClass({ id: 'tag-foo', name: 'Hol & Bringservice', rank: 2 })).toBe('bi-arrow-left-right');
  });
});
