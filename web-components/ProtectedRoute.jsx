import React from "react";
import { useAuth } from "./AuthContext"; // Assume a standard React AuthContext

/**
 * Reusable ProtectedRoute / RoleGuard component to restrict pages/routes by role.
 * Hides unauthorized content and displays a stylized, accessible "Access Denied" message.
 */
export const ProtectedRoute = ({ allowedRoles = [], children }) => {
  const { currentUser, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-slate-950 text-white">
        <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-amber-500"></div>
      </div>
    );
  }

  // Fallback if not authenticated
  if (!currentUser) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-slate-950 px-6 text-center">
        <div className="max-w-md bg-slate-900 border border-slate-800 p-8 rounded-2xl shadow-xl">
          <div className="text-4xl mb-4">🔒</div>
          <h2 className="text-2xl font-bold text-slate-100 mb-2">Authentication Required</h2>
          <p className="text-slate-400 mb-6">You must be logged in to access this POS section.</p>
          <a href="/login" className="px-6 py-2.5 bg-amber-500 text-slate-950 font-bold rounded-lg hover:bg-amber-400 transition-colors">
            Go to Login
          </a>
        </div>
      </div>
    );
  }

  const role = (currentUser.role || "user").toLowerCase().trim();
  const isAllowed = role === "administrator" || allowedRoles.map(r => r.toLowerCase().trim()).includes(role);

  if (!isAllowed) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-slate-950 px-6 text-center">
        <div className="max-w-md bg-slate-900 border border-slate-800 p-8 rounded-2xl shadow-xl">
          <div className="text-5xl mb-4 text-red-500">🛡️</div>
          <h2 className="text-2xl font-bold text-slate-100 mb-2">Access Denied</h2>
          <p className="text-slate-400 mb-6">
            Your current account role <span className="text-amber-400 font-semibold">({currentUser.role})</span> does not have authorization to view this section.
          </p>
          <button 
            onClick={() => window.history.back()} 
            className="px-6 py-2.5 bg-amber-500 text-slate-950 font-bold rounded-lg hover:bg-amber-400 transition-colors"
          >
            GO BACK
          </button>
        </div>
      </div>
    );
  }

  return children;
};
