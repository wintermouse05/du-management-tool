const fs = require('fs');
const path = require('path');
const dirPath = 'd:/Development/DU Management Tool/du-management-backend/src/main/java/org/example/dumanagementbackend/service';

const files = fs.readdirSync(dirPath).filter(file => file.endsWith('.java'));
files.forEach(file => {
    const filePath = path.join(dirPath, file);
    let content = fs.readFileSync(filePath, 'utf8');
    if (content.includes('@Transactional(readOnly = true)')) return;
    if (content.includes('@Service')) {
        if (!content.includes('import org.springframework.transaction.annotation.Transactional;')) {
            content = content.replace('import org.springframework.stereotype.Service;', 'import org.springframework.stereotype.Service;\nimport org.springframework.transaction.annotation.Transactional;');
        }
        content = content.replace(/@Service\s+@RequiredArgsConstructor\s+public class/g, '@Service\n@RequiredArgsConstructor\n@Transactional(readOnly = true)\npublic class');
        content = content.replace(/@Service\s+public class/g, '@Service\n@Transactional(readOnly = true)\npublic class');
        fs.writeFileSync(filePath, content, 'utf8');
        console.log(`Updated ${filePath}`);
    }
});
