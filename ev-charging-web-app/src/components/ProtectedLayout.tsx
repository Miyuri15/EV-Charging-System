import { Outlet, NavLink, useNavigate } from "react-router-dom";
import {
  User,
  LayoutDashboard,
  Users,
  Building,
  Wrench,
  Activity,
} from "lucide-react";
import { roleNavigate, roleRoute } from "./common/RoleBasedAccess";
import { useAuth } from "../context/AuthContext";

export default function ProtectedLayout() {
  const { userRole } = useAuth();
  const navigate = useNavigate();

  const handleRoleNavigate = () => {
    roleNavigate(userRole, navigate);
  };
  return (
    <div className="flex min-h-[calc(100vh-8vh)]">
      {/* Sidebar (only desktop) */}
      <aside className="hidden md:flex flex-col w-64 bg-gray-100 p-4 shadow-lg border-r border-gray-200">
        <nav className="flex flex-col space-y-2">
          <NavLink
            to={roleRoute(userRole)}
            onClick={handleRoleNavigate}
            className={({ isActive }) =>
              `flex items-center gap-2 px-4 py-2 rounded-lg transition ${
                isActive ? "bg-green-600 text-white" : "hover:bg-green-100"
              }`
            }
          >
            <LayoutDashboard className="w-5 h-5" />
            Dashboard
          </NavLink>

          {userRole === "admin" && (
            <NavLink
              to="/admin/users"
              className={({ isActive }) =>
                `flex items-center gap-2 px-4 py-2 rounded-lg transition ${
                  isActive ? "bg-green-600 text-white" : "hover:bg-green-100"
                }`
              }
            >
              <Users className="w-5 h-5" />
              User Management
            </NavLink>
          )}
          {userRole === "admin" && (
            <NavLink
              to="/admin/stations"
              className={({ isActive }) =>
                `flex items-center gap-2 px-4 py-2 rounded-lg transition ${
                  isActive ? "bg-green-600 text-white" : "hover:bg-green-100"
                }`
              }
            >
              <Building className="w-5 h-5" />
              Station Management
            </NavLink>
          )}
        </nav>
        {userRole === "operator" && (
          <nav className="flex flex-col space-y-2">
            <NavLink
              to="/operator/update-slot-availability"
              className={({ isActive }) =>
                `flex items-center gap-2 px-4 py-2 rounded-lg transition ${
                  isActive ? "bg-green-600 text-white" : "hover:bg-green-100"
                }`
              }
            >
              <Wrench className="w-5 h-5" />
              Update Slot Availability
            </NavLink>
            <NavLink
              to="/operator/view-station-status"
              className={({ isActive }) =>
                `flex items-center gap-2 px-4 py-2 rounded-lg transition ${
                  isActive ? "bg-green-600 text-white" : "hover:bg-green-100"
                }`
              }
            >
              <Activity className="w-5 h-5" />
              View Station Status
            </NavLink>
          </nav>
        )}

        <NavLink
          to="/profile"
          className={({ isActive }) =>
            `flex items-center gap-2 px-4 py-2 rounded-lg transition ${
              isActive ? "bg-green-600 text-white" : "hover:bg-green-100"
            }`
          }
        >
          <User className="w-5 h-5" />
          Profile
        </NavLink>
      </aside>

      {/* Page Content */}
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
