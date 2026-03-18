function Modal({ open = false, title = '', children, footer = null, onClose }) {
  if (!open) {
    return null;
  }

  return (
    <div role="dialog" aria-modal="true" aria-label={title || 'Modal'}>
      <div>
        <div>
          <h2>{title}</h2>
          <button type="button" onClick={onClose} aria-label="Close modal">
            Close
          </button>
        </div>
        <div>{children}</div>
        {footer ? <div>{footer}</div> : null}
      </div>
    </div>
  );
}

export default Modal;
