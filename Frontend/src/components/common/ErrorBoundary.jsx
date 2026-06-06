import { Component } from "react";

/**
 * ErrorBoundary — catches render errors, shows fallback UI.
 * Dùng như wrapper: <ErrorBoundary><MyComponent /></ErrorBoundary>
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    console.error("[ErrorBoundary]", error, info);
  }

  handleReset() {
    this.setState({ hasError: false, error: null });
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;

      return (
        <div className="flex flex-col items-center justify-center py-20 text-center px-6">
          <span className="text-4xl mb-4">⚠️</span>
          <p className="text-sm font-semibold text-gray-800 mb-1">
            Đã xảy ra lỗi hiển thị
          </p>
          <p className="text-xs text-gray-400 mb-6 max-w-sm">
            {this.state.error?.message || "Lỗi không xác định."}
          </p>
          <div className="flex gap-3">
            <button
              onClick={() => this.handleReset()}
              className="px-4 py-2 text-sm rounded-lg bg-blue-600 text-white hover:bg-blue-700"
            >
              Thử lại
            </button>
            <button
              onClick={() => window.location.reload()}
              className="px-4 py-2 text-sm rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50"
            >
              Tải lại trang
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
