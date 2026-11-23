package games.orbit.orbitstaff.api.web;

import games.orbit.orbitstaff.common.error.DuplicateActionException;
import games.orbit.orbitstaff.common.error.IntegrationUnavailableException;
import games.orbit.orbitstaff.common.error.NotFoundException;
import games.orbit.orbitstaff.common.error.OrbitStaffException;
import games.orbit.orbitstaff.common.error.PermissionDeniedException;
import games.orbit.orbitstaff.common.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduit les exceptions metier en reponses HTTP homogenes.
 *
 * <p>Aucune erreur n'est masquee : les erreurs inattendues sont journalisees et
 * renvoyees en 500 avec un message generique (pas de fuite de detail interne).</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ValidationException e) {
        return build(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ApiError> handlePermission(PermissionDeniedException e) {
        return build(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(DuplicateActionException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateActionException e) {
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(IntegrationUnavailableException.class)
    public ResponseEntity<ApiError> handleIntegration(IntegrationUnavailableException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, e);
    }

    @ExceptionHandler(OrbitStaffException.class)
    public ResponseEntity<ApiError> handleOrbitStaff(OrbitStaffException e) {
        return build(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Erreur inattendue traitee par l'API", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Erreur interne"));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, OrbitStaffException e) {
        return ResponseEntity.status(status).body(new ApiError(e.errorCode(), e.getMessage()));
    }
}
