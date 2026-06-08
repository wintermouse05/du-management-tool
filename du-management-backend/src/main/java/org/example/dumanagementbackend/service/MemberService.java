package org.example.dumanagementbackend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.dumanagementbackend.dto.member.MemberRequest;
import org.example.dumanagementbackend.dto.member.MemberResponse;
import org.example.dumanagementbackend.dto.member.MemberSkillRequest;
import org.example.dumanagementbackend.dto.member.MemberSkillResponse;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserSkill;
import org.example.dumanagementbackend.entity.enums.MemberSkillType;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.RoleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberService.class);
    private static final int MIN_SKILL_LEVEL = 1;
    private static final int MAX_SKILL_LEVEL = 5;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public MemberResponse create(MemberRequest request) {
        validateCreateUniqueness(request.username(), request.email());
        User user = new User();
        apply(user, request);
        if (user.getTotalPoints() == null) {
            user.setTotalPoints(0);
        }
        return toResponse(userRepository.save(user));
    }

    public Page<MemberResponse> getAll(Pageable pageable, boolean includeAdmins, boolean includeInactive) {
        Pageable resolvedPageable = toZeroBasedPageable(pageable);
        UserStatus visibleStatus = includeInactive ? null : UserStatus.ACTIVE;
        return userRepository.searchMembers("%", visibleStatus, includeAdmins, resolvedPageable).map(this::toResponse);
    }

    public Page<MemberResponse> search(
            String q,
            UserStatus status,
            Pageable pageable,
            boolean includeAdmins,
            boolean includeInactive
    ) {
        String likePattern = normalizeLikePattern(q);
        Pageable resolvedPageable = toZeroBasedPageable(pageable);
        if (!includeInactive && status == UserStatus.INACTIVE) {
            return Page.empty(resolvedPageable);
        }

        UserStatus visibleStatus = includeInactive ? status : UserStatus.ACTIVE;
        return userRepository.searchMembers(likePattern, visibleStatus, includeAdmins, resolvedPageable).map(this::toResponse);
    }

    public byte[] exportCsv(String q, UserStatus status, boolean includeAdmins, boolean includeInactive) {
        String likePattern = normalizeLikePattern(q);
        List<User> users = !includeInactive && status == UserStatus.INACTIVE
                ? List.of()
                : userRepository.searchMembersForExport(
                        likePattern,
                        includeInactive ? status : UserStatus.ACTIVE,
                        includeAdmins
                );

        StringBuilder csv = new StringBuilder();
        csv.append("id,username,email,fullName,role,status,joinDate,tenureMonths,totalPoints,skills\n");
        for (User user : users) {
            csv.append(user.getId()).append(',')
                    .append(csvEscape(user.getUsername())).append(',')
                    .append(csvEscape(user.getEmail())).append(',')
                    .append(csvEscape(user.getFullName())).append(',')
                    .append(csvEscape(user.getRole().getName())).append(',')
                    .append(user.getStatus()).append(',')
                    .append(user.getJoinDate() != null ? user.getJoinDate() : "").append(',')
                    .append(calculateTenureMonths(user.getJoinDate()) != null ? calculateTenureMonths(user.getJoinDate()) : "")
                    .append(',')
                    .append(user.getTotalPoints())
                    .append(',')
                    .append(csvEscape(formatSkillsForCsv(user)))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public int importMembers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        List<Map<String, String>> rows;
        try {
            if (filename.endsWith(".xlsx")) {
                rows = readXlsxRows(file);
            } else if (filename.endsWith(".csv")) {
                rows = readCsvRows(file);
            } else {
                throw new BadRequestException("Only .csv or .xlsx files are supported");
            }
        } catch (IOException ex) {
            LOGGER.error("Unable to read import file. fileName={}", file.getOriginalFilename(), ex);
            throw new BadRequestException("Unable to read import file. Please verify file format and try again.");
        }

        int imported = 0;
        for (Map<String, String> row : rows) {
            String username = value(row, "username");
            String email = value(row, "email");
            String fullName = value(row, "fullname");

            if (username == null || email == null || fullName == null) {
                continue;
            }
            if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
                continue;
            }

            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setFullName(fullName);
            user.setPassword(passwordEncoder.encode(defaultIfBlank(value(row, "password"), "ChangeMe@123")));
            user.setStatus(parseStatus(value(row, "status")));
            user.setDob(parseDate(value(row, "dob")));
            user.setJoinDate(parseDate(value(row, "joindate")));
            user.setRole(resolveRole(value(row, "roleid"), value(row, "rolename")));
            user.getSkills().addAll(toUserSkills(parseSkillRequests(value(row, "skills"))));
            user.setTotalPoints(0);

            userRepository.save(user);
            imported++;
        }

        return imported;
    }

    public MemberResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional
    public MemberResponse update(Long id, MemberRequest request) {
        User user = getEntityById(id);
        boolean wasActive = user.getStatus() == UserStatus.ACTIVE;
        validateUpdateUniqueness(id, request.username(), request.email());
        apply(user, request);
        User saved = userRepository.save(user);
        if (wasActive && saved.getStatus() == UserStatus.INACTIVE) {
            refreshTokenService.revokeActiveByUserId(saved.getId(), "USER_INACTIVE");
        }
        return toResponse(saved);
    }

    @Transactional
    public MemberResponse deactivate(Long id) {
        User user = getEntityById(id);
        user.setStatus(UserStatus.INACTIVE);
        User saved = userRepository.save(user);
        refreshTokenService.revokeActiveByUserId(saved.getId(), "USER_INACTIVE");
        return toResponse(saved);
    }

    public User getEntityById(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + id));
    }

    private void apply(User user, MemberRequest request) {
        Role role = roleRepository.findByIdAndDeletedAtIsNull(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id=" + request.roleId()));
        user.setRole(role);
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setDob(request.dob());
        user.setJoinDate(request.joinDate());
        user.setStatus(request.status() != null ? request.status() : UserStatus.ACTIVE);
        user.getSkills().clear();
        user.getSkills().addAll(toUserSkills(request.skills()));
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        } else if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode("ChangeMe@123"));
        }
    }

    private MemberResponse toResponse(User user) {
        return new MemberResponse(
                user.getId(),
                user.getRole().getId(),
                user.getRole().getName(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getDob(),
                user.getJoinDate(),
                calculateTenureMonths(user.getJoinDate()),
                user.getTotalPoints(),
                user.getStatus(),
                toSkillResponses(user)
        );
    }

    private List<UserSkill> toUserSkills(List<MemberSkillRequest> skillRequests) {
        if (skillRequests == null || skillRequests.isEmpty()) {
            return List.of();
        }

        Set<MemberSkillType> seenSkills = EnumSet.noneOf(MemberSkillType.class);
        List<UserSkill> userSkills = new ArrayList<>();
        for (MemberSkillRequest skillRequest : skillRequests) {
            if (skillRequest == null || skillRequest.skill() == null || skillRequest.level() == null) {
                throw new BadRequestException("Member skill and level are required.");
            }
            if (skillRequest.level() < MIN_SKILL_LEVEL || skillRequest.level() > MAX_SKILL_LEVEL) {
                throw new BadRequestException("Member skill level must be between 1 and 5.");
            }
            if (!seenSkills.add(skillRequest.skill())) {
                throw new BadRequestException("Each member skill can only be added once.");
            }
            userSkills.add(new UserSkill(skillRequest.skill(), skillRequest.level()));
        }

        userSkills.sort(Comparator.comparing(skill -> skill.getSkill().getLabel()));
        return userSkills;
    }

    private List<MemberSkillResponse> toSkillResponses(User user) {
        if (user.getSkills() == null || user.getSkills().isEmpty()) {
            return List.of();
        }

        return user.getSkills().stream()
                .filter(skill -> skill.getSkill() != null)
                .sorted(Comparator.comparing(skill -> skill.getSkill().getLabel()))
                .map(skill -> new MemberSkillResponse(
                        skill.getSkill(),
                        skill.getSkill().getLabel(),
                        skill.getLevel()
                ))
                .toList();
    }

    private String formatSkillsForCsv(User user) {
        return toSkillResponses(user).stream()
                .map(skill -> skill.skillLabel() + ":" + skill.level())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    private String normalizeLikePattern(String q) {
        if (q == null) {
            return "%";
        }

        String trimmed = q.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "%";
        }

        String escaped = trimmed
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private void validateCreateUniqueness(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("AUTH_USERNAME_EXISTS", "Username already exists.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("AUTH_EMAIL_EXISTS", "Email already exists.");
        }
    }

    private void validateUpdateUniqueness(Long userId, String username, String email) {
        if (userRepository.existsByUsernameAndIdNot(username, userId)) {
            throw new BadRequestException("AUTH_USERNAME_EXISTS", "Username already exists.");
        }
        if (userRepository.existsByEmailAndIdNot(email, userId)) {
            throw new BadRequestException("AUTH_EMAIL_EXISTS", "Email already exists.");
        }
    }

    private Pageable toZeroBasedPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return pageable;
        }

        int resolvedPage = Math.max(pageable.getPageNumber() - 1, 0);
        return PageRequest.of(resolvedPage, pageable.getPageSize(), pageable.getSort());
    }

    private Long calculateTenureMonths(LocalDate joinDate) {
        if (joinDate == null) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(joinDate, LocalDate.now());
        return Math.max(months, 0);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private List<Map<String, String>> readCsvRows(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            String[] headers = splitCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] values = splitCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(normalizeHeader(headers[i]), values[i].trim());
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> readXlsxRows(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return rows;
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return rows;
            }

            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                headers.add(normalizeHeader(formatter.formatCellValue(headerRow.getCell(i))));
            }

            for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row rowObj = sheet.getRow(rowNum);
                if (rowObj == null) {
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                boolean hasAnyValue = false;
                for (int i = 0; i < headers.size(); i++) {
                    String cellValue = formatter.formatCellValue(rowObj.getCell(i));
                    if (!cellValue.isBlank()) {
                        hasAnyValue = true;
                    }
                    row.put(headers.get(i), cellValue.trim());
                }
                if (hasAnyValue) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private String[] splitCsvLine(String line) {
        // Keep parser small: handles quoted values and commas.
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values.toArray(new String[0]);
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT).replace("_", "");
    }

    private String value(Map<String, String> row, String key) {
        String raw = row.get(key);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private UserStatus parseStatus(String status) {
        if (status == null) {
            return UserStatus.ACTIVE;
        }
        try {
            return UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UserStatus.ACTIVE;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private List<MemberSkillRequest> parseSkillRequests(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        Set<MemberSkillType> seenSkills = EnumSet.noneOf(MemberSkillType.class);
        List<MemberSkillRequest> skills = new ArrayList<>();
        String[] entries = value.split("[;|]");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split("[:=]", 2);
            MemberSkillType skill = parseSkillType(parts[0]);
            Integer level = parts.length > 1 ? parseSkillLevel(parts[1]) : MIN_SKILL_LEVEL;
            if (skill == null || level == null || !seenSkills.add(skill)) {
                continue;
            }

            skills.add(new MemberSkillRequest(skill, level));
        }

        return skills;
    }

    private MemberSkillType parseSkillType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        for (MemberSkillType skill : MemberSkillType.values()) {
            if (skill.getLabel().equalsIgnoreCase(trimmed) || skill.name().equalsIgnoreCase(trimmed)) {
                return skill;
            }
        }

        String enumName = trimmed.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        try {
            return MemberSkillType.valueOf(enumName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Integer parseSkillLevel(String value) {
        if (value == null) {
            return null;
        }
        try {
            int level = Integer.parseInt(value.trim());
            return level >= MIN_SKILL_LEVEL && level <= MAX_SKILL_LEVEL ? level : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Role resolveRole(String roleIdValue, String roleNameValue) {
        if (roleIdValue != null) {
            try {
                Long roleId = Long.parseLong(roleIdValue);
                return roleRepository.findByIdAndDeletedAtIsNull(roleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found with id=" + roleId));
            } catch (NumberFormatException ignored) {
                // Fallback to role name/default below.
            }
        }

        if (roleNameValue != null) {
            return roleRepository.findByNameAndDeletedAtIsNull(roleNameValue.trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found with name=" + roleNameValue));
        }

        return roleRepository.findByNameAndDeletedAtIsNull("MEMBER")
                .orElseThrow(() -> new ResourceNotFoundException("Role MEMBER is missing"));
    }
}
