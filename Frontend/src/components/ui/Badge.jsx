function Badge({ children, tone = 'neutral' }) {
  return <span data-tone={tone}>{children}</span>;
}

export default Badge;
