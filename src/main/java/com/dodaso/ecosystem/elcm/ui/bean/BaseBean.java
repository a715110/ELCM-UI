package com.dodaso.ecosystem.elcm.ui.bean;

import static com.dodaso.ecosystem.baseline.common.constant.EventTypeEnum.TASK_CREATED;
import static com.dodaso.ecosystem.baseline.common.constant.EventTypeEnum.TASK_UPDATED;

import com.dodaso.ecosystem.auth.dto.UserDTO;
import com.dodaso.ecosystem.auth.dto.UserProfileDTO;
import com.dodaso.ecosystem.baseline.common.constant.EventTypeEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.ecws.dto.CollaborationTaskDTO;
import com.dodaso.ecosystem.ecws.dto.EventLogDTO;
import com.dodaso.ecosystem.ecws.dto.EventTypeDTO;
import com.dodaso.ecosystem.ecws.event.CommentAddEventDTOListener;
import com.dodaso.ecosystem.ecws.event.EventDTODispatcher;
import com.dodaso.ecosystem.elcm.ui.helper.UserHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.apache.commons.lang.WordUtils;
import org.primefaces.PrimeFaces;
import org.primefaces.model.DialogFrameworkOptions;
import org.springframework.beans.factory.annotation.Autowired;

@Data
public abstract class BaseBean implements Serializable {

  protected String selectedTabName;
  @Autowired
  protected RESTServiceClient restServiceClient;
  String action;
  boolean unsavedInd;
  boolean saveDisabledInd;
  EventDTODispatcher eventDTODispatcher;
  List<UserProfileDTO> userProfiles;
  UserDTO userDTO;
  String loginId;

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected UserHelper userHelper;

  @PostConstruct
  private void init() throws Exception {
    userProfiles = userHelper.getUserProfileDTOList();
    loginId = userHelper.getActiveUserProfile().getUserExtDTO().getLoginId();

    //TODO:  enhance this code to support various event types.
    eventDTODispatcher = new EventDTODispatcher();
    eventDTODispatcher.registerListener("ADDED_COMMENT", new CommentAddEventDTOListener());
    eventDTODispatcher.registerListener("YOU_WERE_MENTIONED", new CommentAddEventDTOListener());
    eventDTODispatcher.registerListener("ATTACHED_FILE", new CommentAddEventDTOListener());
    //saveDisabledInd = true;
  }

  protected DialogFrameworkOptions buildDefaultDialogOptions(final String width,
      final String height)
      throws Exception {
    return DialogFrameworkOptions.builder().modal(true).fitViewport(true).responsive(true)
        .width(width)
        .height(height).contentWidth("100%").resizeObserver(true).responsive(true)
        .resizeObserverCenter(true)
        .resizable(false).styleClass("max-w-screen").iframeStyleClass("max-w-screen").build();
  }

  public void closeDialog(final String dialogId) {
    PrimeFaces.current().dialog().closeDynamic(dialogId);
  }

  protected Map<String, String> getRequestParamMap() {
    return FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
  }

  protected void initForCreate() throws Exception {
  }

  protected void initForUpdate() throws Exception {
  }

  public void refresh() {
  }

  public void createSubTask() {
  }

  public void duplicateTask() {
  }

  protected EventLogDTO getEventLogDTO(CollaborationTaskDTO collaborationTaskDTO) throws Exception {
    UserProfileDTO userProfileDTO = userHelper.getActiveUserProfile();
    EventLogDTO eventLogDTO = new EventLogDTO();
    EventTypeDTO eventTypeDTO = new EventTypeDTO();
    eventLogDTO.setUserProfileId(
        3); //TODO:  need to integrate with IAMS --> userProfileDTO.getId());
    eventLogDTO.setCreatedAt(Instant.now());
    eventLogDTO.setCreatedBy("eric@dodaso.com"); //TODO:  need to enhance it holistically
    eventLogDTO.setEventTimestamp(Instant.now());
    eventLogDTO.setReadInd((byte) 0);

    eventTypeDTO.setEventType(EventTypeEnum.TASK.getEventName());
    if (!collaborationTaskDTO.isModified()) {
      eventTypeDTO.setEventName(TASK_CREATED.getEventName());
      eventLogDTO.setEventTypeDTO(eventTypeDTO);
      eventLogDTO.setEventLogTitle(
          WordUtils.capitalizeFully(TASK_CREATED.name().replace("_", " ")));
      eventLogDTO.setEventLogSummary(TASK_CREATED.getDescription());
    } else {
      eventTypeDTO.setEventName(TASK_UPDATED.getEventName());
      eventLogDTO.setEventTypeDTO(eventTypeDTO);
      eventLogDTO.setEventLogTitle(
          WordUtils.capitalizeFully(TASK_UPDATED.name().replace("_", " ")));
      eventLogDTO.setEventLogSummary(TASK_UPDATED.getDescription());
    }
    eventLogDTO.setEventTypeDTO(eventTypeDTO);
    return eventLogDTO;
  }
}