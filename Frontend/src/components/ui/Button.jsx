function Button({
  children,
  type = 'button',
  variant = 'primary',
  className = '',
  ...props
}) {
  const classes = ['ui-button', `ui-button--${variant}`, className]
    .filter(Boolean)
    .join(' ');

  return (
    <button type={type} className={classes} {...props}>
      {children}
    </button>
  );
}

export default Button;
