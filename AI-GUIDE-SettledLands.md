
## Урок Build 272: TOML description обновление (2026-10-31)

**Проблема:** Minecraft вылетал при загрузке с ошибкой:
```
FATAL [net.neoforged.fml.ModLoader/CORE]: Error during pre-loading phase: 
File mods\settledlands-0.8.0-build272-pursuit-single-node.jar is not a valid mod file
com.electronwill.nightconfig.core.io.ParsingException: Invalid bare key: '{'
```

**Причина:** Патч-скрипт обновлял только `version` в `neoforge.mods.toml`, но оставлял `description` от предыдущего билда (Build 271). Night Config парсер NeoForge не смог распарсить старое многострочное описание.

**Решение:** Патч-скрипт должен обновлять **обе** строки:
1. `version="..."` - заменить на новую версию
2. `description='''...'''` - заменить первую строку на CHANGELOG нового билда

**Правильная логика:**
```python
# Обновляем версию
text = text.replace(f'version="{BASE_VERSION}"', f'version="{NEW_VERSION}"')

# Обновляем description - заменяем первую строку
lines = text.split('\n')
for i, line in enumerate(lines):
    if line.startswith("description='''"):
        lines[i] = f"description='''{CHANGELOG}"
        break
text = '\n'.join(lines)
```

**Проверка:** После сборки всегда проверять, что description начинается с CHANGELOG текущего билда:
```bash
python3 -c "import zipfile; z = zipfile.ZipFile('mod.jar'); print(z.read('META-INF/neoforge.mods.toml').decode('utf-8'))" | grep -A 3 "description="
```

**Вывод:** validate_jar.py проверяет структуру, но не семантику TOML. Всегда проверять, что все метаданные (version, description, displayName) обновлены для нового билда.

