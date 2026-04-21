package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.member.MemberRequest;
import org.example.dumanagementbackend.dto.member.MemberResponse;
import org.example.dumanagementbackend.entity.Role;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.RoleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponse create(MemberRequest request) {
        User user = new User();
        apply(user, request);
        if (user.getTotalPoints() == null) {
            user.setTotalPoints(0);
        }
        return toResponse(userRepository.save(user));
    }

    public Page<MemberResponse> getAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<MemberResponse> search(String q, UserStatus status, Pageable pageable) {
        String query = normalizeQuery(q);
        return userRepository.searchMembers(query, status, pageable).map(this::toResponse);
    }

    public byte[] exportCsv(String q, UserStatus status) {
        String query = normalizeQuery(q);
        List<User> users = userRepository.searchMembersForExport(query, status);

        StringBuilder csv = new StringBuilder();
        csv.append("id,username,email,fullName,role,status,joinDate,tenureMonths,totalPoints\n");
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
            throw new BadRequestException("Unable to read import file: " + ex.getMessage());
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
        apply(user, request);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public MemberResponse deactivate(Long id) {
        User user = getEntityById(id);
        user.setStatus(UserStatus.INACTIVE);
        return toResponse(userRepository.save(user));
    }

    public User getEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + id));
    }

    private void apply(User user, MemberRequest request) {
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id=" + request.roleId()));
        user.setRole(role);
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setDob(request.dob());
        user.setJoinDate(request.joinDate());
        user.setStatus(request.status() != null ? request.status() : UserStatus.ACTIVE);
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
                user.getStatus()
        );
    }

    private String normalizeQuery(String q) {
        if (q == null) {
            return null;
        }
        String trimmed = q.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private Role resolveRole(String roleIdValue, String roleNameValue) {
        if (roleIdValue != null) {
            try {
                Long roleId = Long.parseLong(roleIdValue);
                return roleRepository.findById(roleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found with id=" + roleId));
            } catch (NumberFormatException ignored) {
                // Fallback to role name/default below.
            }
        }

        if (roleNameValue != null) {
            return roleRepository.findByName(roleNameValue.trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found with name=" + roleNameValue));
        }

        return roleRepository.findByName("MEMBER")
                .orElseThrow(() -> new ResourceNotFoundException("Role MEMBER is missing"));
    }
}
