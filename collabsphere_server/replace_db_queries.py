import os

routes_dir = 'src/main/kotlin/plugins/routes'

for filename in os.listdir(routes_dir):
    if not filename.endswith('.kt'):
        continue
        
    filepath = os.path.join(routes_dir, filename)
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        
    new_content = []
    
    in_get_block = False
    get_block_depth = 0
    current_depth = 0
    
    i = 0
    while i < len(content):
        # Check for get("...") {
        if not in_get_block and content[i:i+4] == 'get(':
            in_get_block = True
            # Find where the block actually starts
            j = i
            while j < len(content) and content[j] != '{':
                j += 1
            get_block_depth = current_depth
            
        if content[i] == '{':
            current_depth += 1
        elif content[i] == '}':
            current_depth -= 1
            if in_get_block and current_depth == get_block_depth:
                in_get_block = False
                
        if in_get_block and content[i:i+7] == 'dbQuery':
            # ensure it's a whole word
            prev_char = content[i-1] if i > 0 else ' '
            next_char = content[i+7] if i+7 < len(content) else ' '
            
            is_valid_prev = not prev_char.isalnum() and prev_char != '_'
            is_valid_next = not next_char.isalnum() and next_char != '_'
            
            if is_valid_prev and is_valid_next:
                new_content.append('dbReadQuery')
                i += 7
                continue
            
        new_content.append(content[i])
        i += 1
        
    new_string = "".join(new_content)
    
    if new_string != content:
        print(f"Updated {filename}")
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_string)
