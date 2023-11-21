package cat.itacademy.barcelonactiva.abdellaoui.fethi.s05.t02.security.Controllers;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class SecurityController {

	private JwtEncoder jwtEncoder;
	private JwtDecoder jwtDecoder;
	private AuthenticationManager authenticationManager;
	private UserDetailsService userDetailsService;

	@PostMapping("/token")
	public ResponseEntity<Map<String, String>> jwtToken(String grantType, String username, String password,
			boolean ambRefreshToken, String refreshToken) {

		String subject = null;
		String scope = null;
		if (grantType.equals("password")) {

			Authentication authentication = authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(username, password));
			subject = authentication.getName();
			scope = authentication.getAuthorities().stream()
					.map(auth -> auth.getAuthority())
					.collect(Collectors.joining(" "));
		} else if (grantType.equals("refreshToken")) {
			
			if (refreshToken == null) {
				
				return new ResponseEntity<>(Map.of("error", "refresh token required!!"), HttpStatus.UNAUTHORIZED);
			}

			Jwt decodeJWT;
			try {
				decodeJWT = jwtDecoder.decode(refreshToken);
			} catch (JwtException e) {
				
				return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.UNAUTHORIZED);
			}
			subject = decodeJWT.getSubject();
			UserDetails userDetails = userDetailsService.loadUserByUsername(subject);
			Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
			scope = authorities.stream().map(auth -> auth.getAuthority()).collect(Collectors.joining(" "));
		}

		Map<String, String> idToken = new HashMap<>();
		
		Instant instant = Instant.now();
		JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
				.subject(subject)
				.issuedAt(instant)
				.expiresAt(instant.plus(ambRefreshToken ? 5 : 10, ChronoUnit.MINUTES))
				.issuer("security-service")
				.claim("scope", scope)
				.build();

		String jwtAccessToken = jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsSet)).getTokenValue();

		idToken.put("accessToken", jwtAccessToken);
		
		if (ambRefreshToken) {

			JwtClaimsSet jwtClaimsSetRefresh = JwtClaimsSet.builder().subject(subject).issuedAt(instant)
					.expiresAt(instant.plus(30, ChronoUnit.MINUTES)).issuer("security-service").build();

			String jwtRefreshToken = jwtEncoder.encode(JwtEncoderParameters.from(jwtClaimsSetRefresh)).getTokenValue();
			idToken.put("refreshToken", jwtRefreshToken);
		}
		
		return new ResponseEntity<>(idToken, HttpStatus.OK);
	}
}