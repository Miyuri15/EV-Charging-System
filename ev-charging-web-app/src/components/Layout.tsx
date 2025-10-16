import { Link, Outlet, useNavigate } from "react-router-dom";
import { useState, useEffect, useRef } from "react";
import {
  LogOut,
  Menu,
  User,
  X,
  Bell,
  Building,
  Users,
  LayoutDashboard,
  Activity,
  Wrench,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { postRequest, getRequest, patchRequest } from "./common/api";
import toast from "react-hot-toast";
import { HubConnectionBuilder } from "@microsoft/signalr";
import type { Notification } from "../types";
import { roleNavigate } from "./common/RoleBasedAccess";

const Layout: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [showLogoutModal, setShowLogoutModal] = useState(false);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [showNotifications, setShowNotifications] = useState(false);
  const { isAuthenticated, userRole, userId, logout } = useAuth();
  const navigate = useNavigate();
  const notificationsRef = useRef<HTMLDivElement>(null);

  // Fetch notifications and setup SignalR
  useEffect(() => {
    let connection: any;
    if (isAuthenticated && userId) {
      getRequest<Notification[]>(`/notifications/user/${userId}`).then(
        (res) => {
          setNotifications(res?.data ?? []);
        }
      );

      const baseUrl = import.meta.env.VITE_APP_API_BASE_URL.replace("/api", "");

      connection = new HubConnectionBuilder()
        .withUrl(`${baseUrl}/notificationHub`, {
          accessTokenFactory: () => localStorage.getItem("token") || "",
        })
        .withAutomaticReconnect()
        .build();

      connection
        .start()
        .then(() => console.log("✅ SignalR Connected"))
        .catch((err: unknown) => console.error("❌ SignalR Connection Failed:", err));

      connection.on("ReceiveNotification", (notification: any) => {
        setNotifications((prev) => [notification, ...prev]);
        toast.success(notification.message);
      });

      connection.start().catch(console.error);
    }
    return () => {
      if (connection) connection.stop();
    };
  }, [isAuthenticated]);

  type LogoutResponse = {
    success?: boolean;
    [key: string]: any;
  };

  const handleLogout = async () => {
    setShowLogoutModal(true);
  };

  const confirmLogout = async () => {
    setShowLogoutModal(false);
    const response = (await postRequest("/auth/logout")) as LogoutResponse;
    if (response?.status === 200) {
      toast.success("Logout successful!");
    }
    logout();
  };

  const handleRoleNavigate = () => {
    roleNavigate(userRole, navigate);
  };

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        notificationsRef.current &&
        !notificationsRef.current.contains(event.target as Node)
      ) {
        setShowNotifications(false);
      }
    }

    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  return (
    <div className="flex flex-col min-h-screen">
      {/* Navbar */}
      <nav className="fixed top-0 left-0 right-0 bg-gradient-to-r from-green-500 to-emerald-600 text-white p-4 flex items-center justify-between z-50 shadow-md">
        <Link to="/" className="font-bold text-xl">
          EV Charging App
        </Link>

        {/* Desktop Links */}
        <div className="hidden md:flex space-x-4 items-center">
          {isAuthenticated && (
            <button
              onClick={() => setShowNotifications(!showNotifications)}
              className="relative"
              aria-label="Show notifications"
            >
              <Bell className="w-5 h-5" />
              {notifications.some((n) => !n.isRead) && (
                <span className="absolute -top-1 -right-1 bg-red-500 text-white rounded-full text-xs px-1">
                  {notifications.filter((n) => !n.isRead).length}
                </span>
              )}
            </button>
          )}
          {isAuthenticated && (
            <button
              onClick={handleLogout}
              className="ml-4 px-3 py-1 rounded bg-white text-green-700 font-semibold hover:bg-green-100 transition-colors"
            >
              Logout
            </button>
          )}
          {!isAuthenticated && (
            <Link to="/login" className="hover:underline">
              Login
            </Link>
          )}
        </div>

        {/* Mobile Menu Button */}
        <button
          className="md:hidden"
          onClick={() => setIsOpen(!isOpen)}
          aria-label="Toggle Menu"
        >
          {isOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
        </button>
      </nav>
      {/* Mobile Dropdown Menu */}
      {isOpen && (
        <div className="fixed top-16 left-0 right-0 bg-white text-green-900 p-4 space-y-2 md:hidden shadow-lg z-40 rounded-b-xl border-b border-green-200">
          {isAuthenticated && (
            <button
              onClick={() => setShowNotifications(!showNotifications)}
              className="relative flex items-center gap-2 w-full px-4 py-3 rounded-lg hover:bg-green-50 transition"
              aria-label="Show notifications"
            >
              <Bell className="w-5 h-5" />
              <span className="font-semibold">Notifications</span>
              {notifications.some((n) => !n.isRead) && (
                <span className="absolute right-6 top-2 bg-red-500 text-white rounded-full text-xs px-2 py-0.5">
                  {notifications.filter((n) => !n.isRead).length}
                </span>
              )}
            </button>
          )}
          {isAuthenticated && (
            <button
              onClick={handleRoleNavigate}
              className="flex items-center gap-2 w-full px-4 py-3 rounded-lg hover:bg-green-50 transition"
            >
              <LayoutDashboard className="w-5 h-5" />
              <span className="font-semibold">Dashboard</span>
            </button>
          )}
          {isAuthenticated && (
            <Link
              to="/profile"
              className="flex items-center gap-2 w-full px-4 py-3 rounded-lg hover:bg-green-50 transition"
              onClick={() => setIsOpen(false)}
            >
              <User className="w-5 h-5" />
              <span className="font-semibold">Profile</span>
            </Link>
          )}
          {/* need to add User Management and Station Management for admin */}
          {userRole === "admin" && isAuthenticated && (
            <>
              <Link
                to="/admin/users"
                className="flex items-center gap-2 w-full px-4 py-3 rounded-lg hover:bg-green-50 transition"
                onClick={() => setIsOpen(false)}
              >
                <Users className="w-5 h-5" />
                <span className="font-semibold">User Management</span>
              </Link>
              <Link
                to="/admin/stations"
                className="flex items-center gap-2 w-full px-4 py-3 rounded-lg hover:bg-green-50 transition"
                onClick={() => setIsOpen(false)}
              >
                <Building className="w-5 h-5" />
                <span className="font-semibold">Station Management</span>
              </Link>
            </>
          )}
          {/* need to add Update Slot Availability and View Station Status */}
          {userRole === "operator" && isAuthenticated && (
            <>
              <Link
                to="/operator/update-slot-availability"
                className="flex items-center gap-2 w-full px-4 py-3 rounded-lg hover:bg-green-50 transition"
                onClick={() => setIsOpen(false)}
              >
                <Wrench className="w-5 h-5" />
                <span className="font-semibold">Update Slot Availability</span>
              </Link>
              <Link
                to="/operator/view-station-status"
                className="flex items-center gap-2 w-full px-4 py-3 rounded-lg hover:bg-green-50 transition"
                onClick={() => setIsOpen(false)}
              >
                <Activity className="w-5 h-5" />
                <span className="font-semibold">View Station Status</span>
              </Link>
            </>
          )}

          {isAuthenticated && (
            <button
              onClick={() => {
                handleLogout();
                setIsOpen(false);
              }}
              className="flex items-center gap-2 w-full px-4 py-3 rounded-lg bg-red-50 text-red-700 font-semibold hover:bg-red-100 transition mt-2"
            >
              <LogOut className="w-5 h-5" />
              Logout
            </button>
          )}
          {!isAuthenticated && (
            <Link
              to="/login"
              className="flex items-center gap-2 w-full px-4 py-3 rounded-lg hover:bg-green-50 transition"
              onClick={() => setIsOpen(false)}
            >
              <span className="font-semibold">Login</span>
            </Link>
          )}
        </div>
      )}

      {/* Notifications Dropdown (shared for desktop & mobile) */}
      {showNotifications && (
        <div className="fixed inset-0 z-50 flex items-center justify-center backdrop-blur-sm">
          <div
            ref={notificationsRef}
            className="bg-white shadow-lg rounded-lg w-full max-w-sm mx-4 border"
          >
            <div className="p-4 border-b font-bold text-green-700 flex justify-between items-center">
              Notifications
              <button
                className="text-gray-400 hover:text-gray-600"
                onClick={() => setShowNotifications(false)}
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="max-h-80 overflow-y-auto">
              {notifications.length === 0 ? (
                <div className="p-4 text-gray-500 text-center">
                  No notifications
                </div>
              ) : (
                notifications.map((n) => (
                  <div
                    key={n.id}
                    className={`p-4 border-b last:border-b-0 ${
                      n.isRead ? "bg-gray-50" : "bg-green-50"
                    }`}
                  >
                    <div className="font-medium">{n.message}</div>
                    <div className="text-xs text-gray-400">
                      {new Date(n.createdAt).toLocaleString()}
                    </div>
                    {!n.isRead && (
                      <button
                        className="mt-2 text-xs text-green-600 hover:underline"
                        onClick={async () => {
                          await patchRequest(`/notifications/${n.id}/read`);
                          setNotifications((prev) =>
                            prev.map((x) =>
                              x.id === n.id ? { ...x, isRead: true } : x
                            )
                          );
                        }}
                      >
                        Mark as read
                      </button>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {/* Logout Confirmation Modal */}
      {showLogoutModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center backdrop-blur-sm">
          <div className="bg-white rounded-lg shadow-lg p-6 w-full max-w-sm text-center">
            <LogOut className="mx-auto mb-2 text-red-500" size={40} />
            <h2 className="text-lg font-bold mb-2">Confirm Logout</h2>
            <p className="mb-4">
              🚪 Are you sure you want to log out?
              <br />
              You will be redirected to the login page.
            </p>
            <div className="flex justify-center gap-4">
              <button
                onClick={confirmLogout}
                className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 font-semibold"
              >
                Yes, Logout
              </button>
              <button
                onClick={() => setShowLogoutModal(false)}
                className="px-4 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300 font-semibold"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Page Content */}
      <main className="flex-1 flex flex-col pt-16">
        <Outlet />
      </main>
    </div>
  );
};

export default Layout;
