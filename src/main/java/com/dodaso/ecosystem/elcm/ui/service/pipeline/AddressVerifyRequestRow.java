package com.dodaso.ecosystem.elcm.ui.service.pipeline;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ADDED 2026-10-25 -- the address as typed, sent to the service's address check. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressVerifyRequestRow implements Serializable {

  private String line1;
  private String line2;
  private String city;
  private String state;
  private String zip;
}
