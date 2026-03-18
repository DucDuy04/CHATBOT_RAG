export function getInitials(value = '') {
  const words = value
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2);

  if (words.length === 0) {
    return 'NA';
  }

  return words.map((word) => word[0]?.toUpperCase() ?? '').join('');
}

export default getInitials;
