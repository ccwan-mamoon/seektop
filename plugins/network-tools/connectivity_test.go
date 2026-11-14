package main

import (
	"net/http"
	"testing"
)

func TestReachabilityDoesNotPretendAuthenticationOrRegionalAccess(t *testing.T) {
	cases := []struct {
		code       int
		header     http.Header
		body, want string
	}{
		{200, http.Header{}, "ok", "Reachable"},
		{401, http.Header{}, "login", "Reachable, sign-in required"},
		{403, http.Header{}, "denied", "Access restricted"},
		{200, http.Header{"Cf-Mitigated": []string{"challenge"}}, "", "Verification required"},
		{200, http.Header{}, "/cdn-cgi/challenge-platform/", "Verification required"},
		{403, http.Header{}, `{"code":"unsupported_country_region_territory"}`, "Region restricted"},
		{429, http.Header{}, "", "Rate limited"},
		{503, http.Header{}, "", "Service error"},
	}
	for _, c := range cases {
		if got := classifyResponse(c.code, c.header, c.body).En; got != c.want {
			t.Fatalf("HTTP %d: got %q, want %q", c.code, got, c.want)
		}
	}
}
func TestCustomURLsDoNotSendCredentialsOrDowngradeTLS(t *testing.T) {
	for _, raw := range []string{"http://example.com", "https://user:secret@example.com", "file:///etc/passwd", "example.com"} {
		if _, err := checkedURL(raw); err == nil {
			t.Fatalf("accepted %s", raw)
		}
	}
	if _, err := checkedURL("https://[::1]:8443/test"); err != nil {
		t.Fatal(err)
	}
}
