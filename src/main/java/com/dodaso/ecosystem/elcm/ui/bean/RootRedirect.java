package com.dodaso.ecosystem.elcm.ui.bean;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ADDED 2026-10-20. The application address without a page (https://host/elcm/). The SSO sends
 * the browser there after a logout, and it had no page, so the browser showed the error page.
 * Now it goes to the Dashboard, which starts the sign-in when there is no session, and after the
 * sign-in the user's landing page applies (see LandingPageService). Same as ECWS.
 */
@Controller
public class RootRedirect {

  @GetMapping("/")
  public String redirectRoot() {
    return "redirect:/dashboard";
  }
}
