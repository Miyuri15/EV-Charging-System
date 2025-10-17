import { useState, useEffect } from "react";
import {
  getRequestWithPagination,
  patchRequest,
} from "../../components/common/api";
import { useNavigate } from "react-router-dom";
import ConfirmModal from "../../components/common/ConfirmModal";
import formatTime from "../../components/common/formatTime";

interface Booking {
  bookingId: string;
  ownerId?: string;
  stationName?: string;
  startTime?: string;
  endTime?: string;
  createdAt: string | Date;
}

interface PagedResult<T> {
  items: T[];
  totalCount: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

function PendingBookingsPage() {
  const navigate = useNavigate();

  const [pendingBookings, setPendingBookings] = useState<PagedResult<Booking>>({
    items: [],
    totalCount: 0,
    totalPages: 0,
    currentPage: 1,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(true);

  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [currentAction, setCurrentAction] = useState<
    "approve" | "cancel" | null
  >(null);
  const [actionBookingId, setActionBookingId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  // Filters
  const [filters, setFilters] = useState({
    bookingId: "",
    stationName: "",
    date: "",
  });

  const handleFilterChange = (field: keyof typeof filters, value: string) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
  };

  const handleApplyFilters = async () => {
    await fetchPendingBookings(1, pendingBookings.pageSize);
  };

  const handleClearFilters = async () => {
    setFilters({ bookingId: "", stationName: "", date: "" });
    await fetchPendingBookings(1, pendingBookings.pageSize);
  };

  // Fetch pending bookings with pagination
  const fetchPendingBookings = async (pageNumber = 1, pageSize = 10) => {
    setLoading(true);
    try {
      // include filters when fetching
      const queryParams: any = {
        pageNumber,
        pageSize,
      };
      if (filters.bookingId) queryParams.bookingId = filters.bookingId;
      if (filters.stationName) queryParams.stationName = filters.stationName;
      if (filters.date) queryParams.date = filters.date;

      const response = await getRequestWithPagination<Booking>(
        "/bookings/pending",
        queryParams
      );
      if (response?.data) setPendingBookings(response.data);
    } catch (error) {
      console.error("Error fetching pending bookings:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPendingBookings();
  }, []);

  // Helper to clear single filter
  const handleClearSingleFilter = async (field: keyof typeof filters) => {
    setFilters((prev) => ({ ...prev, [field]: "" }));
    await fetchPendingBookings(1, pendingBookings.pageSize);
  };

  const handleApproveBooking = (bookingId: string) => {
    setActionBookingId(bookingId);
    setCurrentAction("approve");
    setShowConfirmModal(true);
  };

  const handleCancelBooking = (bookingId: string) => {
    setActionBookingId(bookingId);
    setCurrentAction("cancel");
    setShowConfirmModal(true);
  };

  const confirmAction = async () => {
    if (!actionBookingId || !currentAction) return;

    setActionLoading(actionBookingId);

    try {
      const endpoint =
        currentAction === "approve"
          ? `/bookings/${actionBookingId}/approve`
          : `/bookings/${actionBookingId}/cancel`;

      const response = await patchRequest(endpoint, {});

      if (!response || response.status !== 200) {
        throw new Error(`Failed to ${currentAction} booking`);
      }

      await fetchPendingBookings(
        pendingBookings.currentPage,
        pendingBookings.pageSize
      );
    } catch (error) {
      console.error(`Error ${currentAction} booking:`, error);
    } finally {
      setShowConfirmModal(false);
      setActionLoading(null);
      setCurrentAction(null);
      setActionBookingId(null);
    }
  };

  const cancelAction = () => {
    setShowConfirmModal(false);
    setCurrentAction(null);
    setActionBookingId(null);
  };

  const handlePageChange = (page: number) => {
    fetchPendingBookings(page, pendingBookings.pageSize);
  };

  // Format date for display (without time)
  const formatDate = (dateString: string | Date) => {
    return new Date(dateString).toLocaleDateString("en-US", {
      timeZone: "Asia/Colombo",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    });
  };

  // Format datetime for created date (with time)
  const formatDateTime = (dateString: string | Date) => {
    return new Date(dateString).toLocaleString("en-US", {
      timeZone: "Asia/Colombo",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
  };

  // Render active filters badges
  const ActiveFilters = () => {
    const hasAny = filters.bookingId || filters.stationName || filters.date;
    if (!hasAny) return null;
    return (
      <div className="mt-4 flex flex-wrap gap-2">
        {filters.bookingId && (
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
            Booking ID: {filters.bookingId}
            <button
              onClick={() => handleClearSingleFilter("bookingId")}
              className="ml-2 hover:text-blue-600 font-bold"
            >
              ×
            </button>
          </span>
        )}
        {filters.stationName && (
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
            Station: {filters.stationName}
            <button
              onClick={() => handleClearSingleFilter("stationName")}
              className="ml-2 hover:text-green-600 font-bold"
            >
              ×
            </button>
          </span>
        )}
        {filters.date && (
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-purple-100 text-purple-800">
            Date: {filters.date}
            <button
              onClick={() => handleClearSingleFilter("date")}
              className="ml-2 hover:text-purple-600 font-bold"
            >
              ×
            </button>
          </span>
        )}
        <button
          onClick={handleClearFilters}
          className="ml-2 px-3 py-1 rounded-full text-xs bg-gray-200 text-gray-700"
        >
          Clear All
        </button>
      </div>
    );
  };

  // Loading Spinner
  const LoadingSpinner = () => (
    <div className="flex justify-center items-center py-20">
      <div className="animate-spin rounded-full h-16 w-16 border-t-4 border-blue-600 border-solid"></div>
    </div>
  );

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-3xl font-semibold text-gray-800">
          Pending Bookings
        </h2>
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-500">Total pending</span>
          <span className="inline-flex items-center px-3 py-1 rounded-full bg-gray-100 text-gray-800 font-medium">
            {pendingBookings.totalCount}
          </span>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl p-4 shadow-sm border border-gray-200 mb-4">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <div>
            <label className="block text-sm text-gray-600 mb-1">
              Booking ID
            </label>
            <input
              type="text"
              value={filters.bookingId}
              onChange={(e) => handleFilterChange("bookingId", e.target.value)}
              placeholder="Search by Booking ID"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </div>
          <div>
            <label className="block text-sm text-gray-600 mb-1">Station</label>
            <input
              type="text"
              value={filters.stationName}
              onChange={(e) =>
                handleFilterChange("stationName", e.target.value)
              }
              placeholder="Search by Station"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </div>
          <div>
            <label className="block text-sm text-gray-600 mb-1">Date</label>
            <input
              type="date"
              value={filters.date}
              onChange={(e) => handleFilterChange("date", e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </div>
          <div className="flex items-end gap-2">
            <button
              onClick={handleApplyFilters}
              className="bg-blue-600 text-white px-4 py-2 rounded-lg"
            >
              Apply
            </button>
            <button
              onClick={handleClearFilters}
              className="bg-gray-500 text-white px-4 py-2 rounded-lg"
            >
              Clear
            </button>
          </div>
        </div>
        <ActiveFilters />
      </div>

      {loading ? (
        <LoadingSpinner />
      ) : pendingBookings.items.length === 0 ? (
        <p className="text-gray-500">No pending bookings available.</p>
      ) : (
        <>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {pendingBookings.items.map((booking) => (
              <div
                key={booking.bookingId}
                className="bg-white shadow-lg rounded-lg p-6 hover:shadow-xl transition-all flex flex-col border border-gray-200"
              >
                {/* Booking ID - Full width */}
                <div className="mb-4">
                  <p className="text-lg font-semibold text-gray-700 break-words bg-gray-50 px-3 py-2 rounded-md">
                    {booking.bookingId || "N/A"}
                  </p>
                </div>

                {/* Action Buttons - Full width */}
                <div className="flex gap-2 mb-4">
                  <button
                    onClick={() => handleApproveBooking(booking.bookingId)}
                    disabled={actionLoading === booking.bookingId}
                    className="flex-1 bg-green-600 text-white px-3 py-2 rounded-lg hover:bg-green-700 disabled:bg-green-400 transition-all text-sm font-medium whitespace-nowrap flex items-center justify-center min-h-[42px]"
                  >
                    {actionLoading === booking.bookingId &&
                    currentAction === "approve" ? (
                      <div className="flex items-center justify-center">
                        <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"></div>
                        Approving...
                      </div>
                    ) : (
                      "Approve"
                    )}
                  </button>
                  <button
                    onClick={() => handleCancelBooking(booking.bookingId)}
                    disabled={actionLoading === booking.bookingId}
                    className="flex-1 bg-red-600 text-white px-3 py-2 rounded-lg hover:bg-red-700 disabled:bg-red-400 transition-all text-sm font-medium whitespace-nowrap flex items-center justify-center min-h-[42px]"
                  >
                    {actionLoading === booking.bookingId &&
                    currentAction === "cancel" ? (
                      <div className="flex items-center justify-center">
                        <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"></div>
                        Cancelling...
                      </div>
                    ) : (
                      "Cancel"
                    )}
                  </button>
                </div>

                {/* Booking Details */}
                <div className="space-y-3 text-sm text-gray-600 flex-1 mb-4">
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Owner Id:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {booking.ownerId || "N/A"}
                    </span>
                  </div>
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Station:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {booking.stationName || "N/A"}
                    </span>
                  </div>

                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Charging Date:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {booking.startTime
                        ? formatDate(booking.startTime)
                        : "N/A"}
                    </span>
                  </div>

                  {/* Time Slot */}
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Time Slot:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {booking.startTime && booking.endTime
                        ? `${formatTime(booking.startTime)} - ${formatTime(
                            booking.endTime
                          )}`
                        : "N/A"}
                    </span>
                  </div>

                  {/* Created Date (with time) */}
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Created At:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {formatDateTime(booking.createdAt)}
                    </span>
                  </div>
                </div>

                {/* View Details Button */}
                <button
                  onClick={() =>
                    navigate(`/admin/bookings/${booking.bookingId}`)
                  }
                  className="w-full bg-blue-600 text-white px-4 py-3 rounded-lg hover:bg-blue-700 transition-all font-medium mt-auto"
                >
                  View Details
                </button>
              </div>
            ))}
          </div>

          {/* Pagination */}
          {pendingBookings.totalPages > 1 && (
            <div className="flex justify-center mt-6 space-x-2">
              {Array.from(
                { length: pendingBookings.totalPages },
                (_, i) => i + 1
              ).map((page) => (
                <button
                  key={page}
                  onClick={() => handlePageChange(page)}
                  className={`px-3 py-1 rounded-md border ${
                    page === pendingBookings.currentPage
                      ? "bg-blue-600 text-white"
                      : "bg-white text-gray-700"
                  }`}
                >
                  {page}
                </button>
              ))}
            </div>
          )}
        </>
      )}

      {showConfirmModal && (
        <ConfirmModal
          title={
            currentAction === "approve"
              ? "Confirm Approval"
              : "Confirm Cancellation"
          }
          message={
            currentAction === "approve"
              ? "Are you sure you want to approve this booking?"
              : "Are you sure you want to cancel this booking?"
          }
          confirmText={
            currentAction === "approve" ? "Yes, Approve" : "Yes, Cancel"
          }
          confirmColor={currentAction === "approve" ? "green" : "red"}
          onConfirm={confirmAction}
          onCancel={cancelAction}
          loading={actionLoading === actionBookingId}
        />
      )}
    </div>
  );
}

export default PendingBookingsPage;
