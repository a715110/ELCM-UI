package com.dodaso.ecosystem.elcm.ui.helper;

import com.dodaso.ecosystem.auth.container.UserDTOContainer;
import com.dodaso.ecosystem.auth.container.UserProfileDTOContainer;
import com.dodaso.ecosystem.auth.dto.UserDTO;
import com.dodaso.ecosystem.auth.dto.UserProfileDTO;
import com.dodaso.ecosystem.auth.dto.ext.UserExtDTO;
import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.container.RESTReqContainer;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import com.dodaso.ecosystem.baseline.common.security.AuthenticationUtil;
import com.dodaso.ecosystem.auth.constant.UserControllerAPIEnum;
import com.dodaso.ecosystem.auth.constant.UserProfileControllerAPIEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Named
@Component
@Slf4j
public class UserHelper {

  @Autowired
  AuthenticationUtil authenticationUtil;

  @Autowired
  RESTServiceClient restServiceClient;

  @Setter
  private String userProfilesJson;

  List<UserProfileDTO> userProfiles;

  // ObjectMapper for JSON conversion
  final ObjectMapper objectMapper = new ObjectMapper();

  public UserHelper() {
  }

//  public String getLoginId() throws Exception {
//    UserDTOContainer userDTOContainer = getUserDTOContainer();
//
//    RESTReqContainer<UserDTOContainer> restReqContainer = new RESTReqContainer<>(
//        ServiceDiscoveryEnum.iams_service.getServiceDiscoveryName(),
//        UserControllerAPIEnum.userControllerAPIEnum_getUserByEmail.getEndPoint(),
//        userDTOContainer,
//        new ParameterizedTypeReference<UserDTOContainer>() {
//        },
//        HttpMethod.POST);
//
//    userDTOContainer = restServiceClient.callRESTService(restReqContainer);
//    return userDTOContainer.getUserDTO().getLoginId();
//  }

//  public UserDTO getUserDTO() throws Exception {
//    UserDTOContainer userDTOContainer = getUserDTOContainer();
//
//    RESTReqContainer<UserDTOContainer> restReqContainer = new RESTReqContainer<>(
//        ServiceDiscoveryEnum.iams_service.getServiceDiscoveryName(),
//        UserControllerAPIEnum.userControllerAPIEnum_getUserByEmail.getEndPoint(),
//        userDTOContainer,
//        new ParameterizedTypeReference<UserDTOContainer>() {
//        },
//        HttpMethod.POST);
//
//    userDTOContainer = restServiceClient.callRESTService(restReqContainer);
//    return userDTOContainer.getUserDTO();
//  }

  public UserProfileDTO getActiveUserProfile() throws Exception {
    UserDTOContainer userDTOContainer = getUserDTOContainer();

    RESTReqContainer<UserDTOContainer> restReqContainer = new RESTReqContainer<>(
        ServiceDiscoveryEnum.iams_service.getServiceDiscoveryName(),
        UserControllerAPIEnum.userControllerAPIEnum_getUserByLoginId.getEndPoint() +
            "?loginId=" + userDTOContainer.getUserDTO().getLoginId(),
        userDTOContainer,
        new ParameterizedTypeReference<UserDTOContainer>() {
        },
        HttpMethod.GET);

    userDTOContainer = restServiceClient.callRESTService(restReqContainer);

    // Null safety check
    if (userDTOContainer == null || userDTOContainer.getUserDTO() == null) {
      throw new RuntimeException("User data not found or invalid");
    }

    List<UserProfileDTO> userProfileDTOList = userDTOContainer.getUserDTO().getUserProfileDTOList();

    // Check if profile list exists and is not empty
    if (userProfileDTOList == null || userProfileDTOList.isEmpty()) {
      throw new RuntimeException("No user profiles found for user: " +
          userDTOContainer.getUserDTO().getLoginId());
    }

    Optional<UserProfileDTO> activeUserProfile = userProfileDTOList
        .stream()
        .filter(profile -> profile != null &&
            profile.getActiveInd() != null &&
            profile.getActiveInd() == 1)
        .findFirst();

    // Handle the Optional properly - set UserDTO only if profile exists
    if (activeUserProfile.isPresent()) {
      UserProfileDTO profile = activeUserProfile.get();
      UserExtDTO userExtDTO = new UserExtDTO();
      userExtDTO.setLoginId(userDTOContainer.getUserDTO().getLoginId());
      profile.setUserExtDTO(userExtDTO);
      return profile;
    }

    // Throw runtime exception if no active user profile is found
    throw new RuntimeException("No active user profile found for user: " +
        userDTOContainer.getUserDTO().getLoginId());
  }

  private UserDTOContainer getUserDTOContainer() throws Exception {
    UserDTOContainer userDTOContainer = new UserDTOContainer();
    UserDTO userDTO = new UserDTO();
    userDTO.setLoginId(authenticationUtil.getUsername());
    userDTOContainer.setUserDTO(userDTO);
    return userDTOContainer;
  }

  public List<UserProfileDTO> getUserProfileDTOList() throws Exception {
    if(userProfiles != null && !userProfiles.isEmpty()) {
      return userProfiles;
    }

    UserProfileDTOContainer userDTOContainer = new UserProfileDTOContainer();
    RESTReqContainer<UserProfileDTOContainer> restReqContainer = new RESTReqContainer<>(
        ServiceDiscoveryEnum.iams_service.getServiceDiscoveryName(),
        UserProfileControllerAPIEnum.userProfileControllerAPIEnum_getUserProfiles.getEndPoint(),
        userDTOContainer,
        new ParameterizedTypeReference<UserProfileDTOContainer>() {
        },
        HttpMethod.GET);

    userDTOContainer = restServiceClient.callRESTService(restReqContainer);
    userProfiles = userDTOContainer.getUserProfileDTOList();
    return userProfiles;
  }

  // Add this method to UserHelper class
  public UserProfileDTO getUserProfileByLoginId(String loginId) throws Exception {
    UserProfileDTO found = Optional.ofNullable(userProfiles)
        .orElse(Collections.emptyList())
        .stream()
        .filter(dto -> dto.getUserExtDTO() != null)
        .filter(dto -> loginId.equals(dto.getUserExtDTO().getLoginId()))
        .findFirst()
        .orElse(null);

    if (found != null) {
      return found;
    }

    UserDTOContainer userDTOContainer = new UserDTOContainer();
    UserDTO userDTO = new UserDTO();
    userDTO.setLoginId(loginId);
    userDTOContainer.setUserDTO(userDTO);

    RESTReqContainer<UserDTOContainer> restReqContainer = new RESTReqContainer<>(
        ServiceDiscoveryEnum.iams_service.getServiceDiscoveryName(),
        UserControllerAPIEnum.userControllerAPIEnum_getUserByLoginId.getEndPoint() +
            "?loginId=" + loginId,
        userDTOContainer,
        new ParameterizedTypeReference<UserDTOContainer>() {
        },
        HttpMethod.GET);

    userDTOContainer = restServiceClient.callRESTService(restReqContainer);

    if (userDTOContainer != null && userDTOContainer.getUserDTO() != null) {
      List<UserProfileDTO> userProfileDTOList = userDTOContainer.getUserDTO()
          .getUserProfileDTOList();

      if (userProfileDTOList != null && !userProfileDTOList.isEmpty()) {
        Optional<UserProfileDTO> activeUserProfile = userProfileDTOList
            .stream()
            .filter(profile -> profile != null &&
                profile.getActiveInd() != null &&
                profile.getActiveInd() == 1)
            .findFirst();

        if (activeUserProfile.isPresent()) {
          return activeUserProfile.get();
        }
      }
    }

    return null; // or throw exception based on your preference
  }

  /**
   * Method called by RemoteCommand to load user profiles for mentions Simply converts the
   * List<UserProfileDTO> to JSON string
   */
  public void loadUserProfilesForMentions() {
    try {
      List<UserProfileDTO> userProfiles = getUserProfileDTOList();

      // Convert directly to JSON string - let JavaScript handle the transformation
      userProfilesJson = objectMapper.writeValueAsString(userProfiles);

      log.info("Loaded {} user profiles as JSON for mentions : {}", userProfiles.size(),
          userProfilesJson);

    } catch (Exception e) {
      userProfilesJson = "[]"; // Empty array as fallback
    }
  }

  // Getter for the JSON data (used by the hidden input)
  public String getUserProfilesJson() {
    return userProfilesJson != null ? userProfilesJson : "[]";
  }


  public String getDisplayNameForUser(String loginId) throws Exception {
    UserProfileDTO userProfile = getUserProfileByLoginId(loginId);
    if (userProfile != null) {
      String firstName = userProfile.getFirstName();
      String lastName = userProfile.getLastName();

      if (firstName != null && lastName != null) {
        return firstName + " " + lastName;
      }
    }

    return loginId; // Fallback to login ID
  }
}