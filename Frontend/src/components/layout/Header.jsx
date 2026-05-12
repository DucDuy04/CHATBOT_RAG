export default function Header({ pageTitle, rightSlot, onHamburger }) {
  return (
    <header className="h-12 bg-white border-b flex items-center px-4 gap-3 shrink-0">
      {/* Hamburger — chỉ hiện khi có handler (mobile) */}
      {onHamburger && (
        <button
          onClick={onHamburger}
          className="text-gray-500 hover:text-gray-800 lg:hidden"
          aria-label="Mở menu"
        >
          <span className="text-xl leading-none">☰</span>
        </button>
      )}

      {/* Page title */}
      <h1 className="flex-1 text-sm font-semibold text-gray-800 truncate">
        {pageTitle}
      </h1>

      {/* Right slot cho action buttons */}
      {rightSlot && (
        <div className="flex items-center gap-2 shrink-0">{rightSlot}</div>
      )}
    </header>
  );
}
