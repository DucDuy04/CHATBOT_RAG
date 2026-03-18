function Sidebar({ items = [], activePath = '', onNavigate }) {
  return (
    <aside>
      <h2>Admin Navigation</h2>
      <nav>
        <ul>
          {items.length === 0 ? (
            <li>No navigation items yet.</li>
          ) : (
            items.map((item) => (
              <li key={item.path}>
                <button
                  type="button"
                  onClick={() => onNavigate?.(item.path)}
                  aria-current={activePath === item.path ? 'page' : undefined}
                >
                  {item.label}
                </button>
              </li>
            ))
          )}
        </ul>
      </nav>
    </aside>
  );
}

export default Sidebar;
