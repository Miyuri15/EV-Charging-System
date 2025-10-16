import { useState, useEffect } from "react";
import { getRequest, patchRequest } from "../../components/common/api";
import { useNavigate } from "react-router-dom";
import ConfirmModal from "../../components/common/ConfirmModal";
import formatTime from "../../components/common/formatTime";

function PendingBookingsPage() {
  const navigate = useNavigate();

  const [pendingBookings, setPendingBookings] = useState([]);
  const [loading, setLoading] = useState(true);

  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [currentAction, setCurrentAction] = useState<
    "approve" | "cancel" | null
  >(null);
  const [actionBookingId, setActionBookingId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  // Fetch pending bookings
  const fetchPendingBookings = async () => {
    setLoading(true);
    try {
      const response = await getRequest("/bookings/pending");
      if (response) setPendingBookings(response.data);
    } catch (error) {
      console.error("Error fetching pending bookings:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPendingBookings();
  }, []);

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

      await fetchPendingBookings();
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

  // Loading Spinner
  const LoadingSpinner = () => (
    <div className="flex justify-center items-center py-20">
      <div className="animate-spin rounded-full h-16 w-16 border-t-4 border-blue-600 border-solid"></div>
    </div>
  );

  return (
    <div className="p-6 space-y-6">
      <h2 className="text-3xl font-semibold text-gray-800">Pending Bookings</h2>

      {loading ? (
        <LoadingSpinner />
      ) : pendingBookings.length === 0 ? (
        <p className="text-gray-500">No pending bookings available.</p>
      ) : (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {pendingBookings.map((booking) => (
            <div
              key={booking.bookingId}
              className="bg-white shadow-lg rounded-lg p-6 hover:shadow-xl transition-all flex flex-col justify-between"
            >
              {/* Booking Header */}
              <div className="flex justify-between items-center mb-4">
                <p className="text-lg font-semibold text-gray-700">
                  {booking.bookingId || "N/A"}
                </p>
                <div className="flex space-x-2">
                  <button
                    onClick={() => handleApproveBooking(booking.bookingId)}
                    className="bg-green-600 text-white px-3 py-1 rounded-lg hover:bg-green-700 transition-all text-sm"
                  >
                    Approve
                  </button>
                  <button
                    onClick={() => handleCancelBooking(booking.bookingId)}
                    className="bg-red-600 text-white px-3 py-1 rounded-lg hover:bg-red-700 transition-all text-sm"
                  >
                    Cancel
                  </button>
                </div>
              </div>

              {/* Booking Details */}
              <div className="space-y-2 text-sm text-gray-600">
                <div className="flex justify-between">
                  <span>Owner Id:</span>
                  <span className="font-semibold">
                    {booking.ownerId || "N/A"}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span>Station:</span>
                  <span className="font-semibold">
                    {booking.stationName || "N/A"}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span>Time Slot:</span>
                  <span className="font-semibold">
                    {booking.startTime && booking.endTime
                      ? `${formatTime(booking.startTime)} - ${formatTime(
                          booking.endTime
                        )}`
                      : ""}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span>Created At:</span>
                  <span className="font-semibold">
                    {new Date(booking.createdAt).toLocaleString("en-US", {
                      timeZone: "Asia/Colombo",
                      year: "numeric",
                      month: "2-digit",
                      day: "2-digit",
                      hour: "2-digit",
                      minute: "2-digit",
                      hour12: false,
                    })}
                  </span>
                </div>
              </div>

              {/* View Details Button */}
              <button
                onClick={() => navigate(`/admin/bookings/${booking.bookingId}`)}
                className="mt-4 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-all"
              >
                View Details
              </button>
            </div>
          ))}
        </div>
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
