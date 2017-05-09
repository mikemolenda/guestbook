package com.cis498.group4.controllers;

import com.cis498.group4.data.AttendanceDataAccess;
import com.cis498.group4.data.EventDataAccess;
import com.cis498.group4.data.UserDataAccess;
import com.cis498.group4.models.Attendance;
import com.cis498.group4.models.Event;
import com.cis498.group4.models.User;
import com.cis498.group4.util.AttendanceHelpers;
import com.cis498.group4.util.SessionHelpers;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The AddRegistration servlet responds to requests to register users for an event.
 */
@WebServlet(name = "AddRegistration", urlPatterns = "/manager/add-registration")
public class AddRegistration extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private AttendanceDataAccess attendanceData;
    private EventDataAccess eventData;
    private UserDataAccess userData;

    public AddRegistration() {
        super();
        attendanceData = new AttendanceDataAccess();
        eventData = new EventDataAccess();
        userData = new UserDataAccess();
    }

    /**
     * Render a form to collect the user info to register them for an event
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // Restrict access by non-Organizers
        if (!SessionHelpers.checkOrganizer(request.getSession())) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN, "You do not have permission to access this resource");
            return;
        }

        String url = "/WEB-INF/views/add-registration.jsp";

        Event event = eventData.getEvent(Integer.parseInt(request.getParameter("id")));
        String eventDate = event.getStartDateTime().format(DateTimeFormatter.ofPattern("M/d/YY"));
        String pageTitle = String.format("Registration for %s %s", event.getName(), eventDate);

        request.setAttribute("event", event);
        request.setAttribute("eventDate", eventDate);
        request.setAttribute("pageTitle", pageTitle);

        RequestDispatcher view = request.getRequestDispatcher(url);
        view.forward(request, response);

    }

    /**
     * Create a new registration (attendance) record from posted data, write to database, and send confirmation to user
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // Restrict access by non-Organizers
        if (!SessionHelpers.checkOrganizer(request.getSession())) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN, "You do not have permission to access this resource");
            return;
        }

        String url;
        String pageTitle;
        String statusMessage;
        String statusType;

        User user = userData.getUserByEmail(request.getParameter("email"));
        Event event = eventData.getEvent(Integer.parseInt(request.getParameter("eventId")));
        String eventDate = event.getStartDateTime().format(DateTimeFormatter.ofPattern("M/d/YY"));

        url = String.format("/WEB-INF/views/add-registration.jsp?id=%s", event.getId());
        pageTitle = String.format("Registration for %s %s", event.getName(), eventDate);

        // Get existing user registrations
        List<Attendance> registrations = attendanceData.getFutureRegistrations(user);

        // Perform appropriate registration action / respond with appropriate message
        int status = AttendanceHelpers.registerStatus(user, event, registrations);

        switch(status) {
            case AttendanceHelpers.ACTION_CLOSED_REGISTRATION:
            case AttendanceHelpers.SUCCESS:
                int insertStatus = attendanceData.insertAttendance(user, event);
                if (insertStatus == 0) {
                    statusMessage = String.format("Successfully registered %s %s for %s!",
                            user.getFirstName(), user.getLastName(), event.getName());
                    statusType = "success";
                } else if (insertStatus == 1062) {
                    statusMessage = String.format("%s %s is already registered for %s!",
                            user.getFirstName(), user.getLastName(), event.getName());
                    statusType = "warning";
                } else {
                    statusMessage = String.format(
                            "<strong>Error!</strong> Could not process the registration for %s %s for %s (%d)",
                            user.getFirstName(), user.getLastName(), event.getName(), insertStatus);
                    statusType = "danger";
                }
                break;
            case AttendanceHelpers.FAIL_INVALID_EVENT:
                statusMessage = "<strong>Error!</strong> Event not found!";
                statusType = "danger";
                break;
            case AttendanceHelpers.FAIL_INVALID_USER:
                statusMessage = "<strong>Error!</strong> User not found!";
                statusType = "danger";
                break;
            case AttendanceHelpers.FAIL_EVENT_ENDED:
                statusMessage = String.format("<strong>Error!</strong> %s has already concluded.", event.getName());
                statusType = "danger";
                break;
            case AttendanceHelpers.FAIL_EVENT_FULL:
                statusMessage = String.format("<strong>Error!</strong> The event %s is at capacity.", event.getName());
                statusType = "danger";
                break;
            case AttendanceHelpers.FAIL_REG_OVERLAP:
                statusMessage = String.format(
                        "<strong>Error!</strong> %s overlaps one of the existing registrations for %s %s.",
                        event.getName(), user.getFirstName(), user.getLastName());
                statusType = "danger";
                break;
            default:
                statusMessage = "<strong>Error!</strong> There was a problem processing the registration.";
                statusType = "danger";
                break;
        }

        request.setAttribute("event", event);
        request.setAttribute("eventDate", eventDate);
        request.setAttribute("pageTitle", pageTitle);
        request.setAttribute("statusMessage", statusMessage);
        request.setAttribute("statusType", statusType);

        RequestDispatcher view = request.getRequestDispatcher(url);
        view.forward(request, response);

    }

}
