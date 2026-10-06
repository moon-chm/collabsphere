import os
import re

routes_dir = 'src/main/kotlin/plugins/routes'

for filename in os.listdir(routes_dir):
    if not filename.endswith('.kt'):
        continue
        
    filepath = os.path.join(routes_dir, filename)
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        
    new_content = []
    
    in_mod_block = False
    mod_block_depth = 0
    current_depth = 0
    
    i = 0
    while i < len(content):
        # Check for post/put/patch/delete("...") {
        if not in_mod_block:
            if content[i:i+5] == 'post(' or content[i:i+4] == 'put(' or content[i:i+6] == 'patch(' or content[i:i+7] == 'delete(':
                in_mod_block = True
                mod_block_depth = current_depth
                
        if content[i] == '{':
            current_depth += 1
        elif content[i] == '}':
            current_depth -= 1
            if in_mod_block and current_depth == mod_block_depth:
                in_mod_block = False
                
        if in_mod_block:
            if content[i:i+11] == 'dbReadQuery':
                prev_char = content[i-1] if i > 0 else ' '
                next_char = content[i+11] if i+11 < len(content) else ' '
                if (not prev_char.isalnum() and prev_char != '_') and (not next_char.isalnum() and next_char != '_'):
                    new_content.append('dbQuery')
                    i += 11
                    continue
            elif content[i:i+18] == 'return@dbReadQuery':
                new_content.append('return@dbQuery')
                i += 18
                continue
            
        new_content.append(content[i])
        i += 1
        
    new_string = "".join(new_content)
    
    if new_string != content:
        print(f"Fixed {filename}")
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_string)
