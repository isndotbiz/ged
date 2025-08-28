# GitHub Authentication Setup

GitHub no longer supports password authentication. You have two options:

## Option 1: Personal Access Token (Recommended)

### Step 1: Create a Personal Access Token
1. Go to GitHub.com and log in
2. Click your profile picture → **Settings**
3. Scroll down and click **Developer settings**
4. Click **Personal access tokens** → **Tokens (classic)**
5. Click **Generate new token** → **Generate new token (classic)**
6. Give it a name like "GEDCOM Processing"
7. Set expiration (recommend 90 days for security)
8. Check these scopes:
   - ✅ **repo** (Full control of private repositories)
   - ✅ **workflow** (Update GitHub Action workflows)
9. Click **Generate token**
10. **COPY THE TOKEN NOW** - you won't see it again!

### Step 2: Use the Token
```bash
# When prompted for password, paste your Personal Access Token
git push -u origin merge-gedfix-architecture
```

## Option 2: SSH Key (More Secure)

### Step 1: Generate SSH Key
```bash
# Generate SSH key (press Enter for defaults)
ssh-keygen -t ed25519 -C "your_email@example.com"

# Start SSH agent
eval "$(ssh-agent -s)"

# Add SSH key
ssh-add ~/.ssh/id_ed25519
```

### Step 2: Add Key to GitHub
```bash
# Copy your public key
cat ~/.ssh/id_ed25519.pub
```

1. Go to GitHub → Settings → SSH and GPG keys
2. Click "New SSH key"
3. Paste your public key
4. Click "Add SSH key"

### Step 3: Change Remote to SSH
```bash
# Change remote URL to SSH
git remote set-url origin git@github.com:isndotbiz/ged.git

# Push using SSH
git push -u origin merge-gedfix-architecture
```

## Quick Fix (If you just want to upload now)

**Use Personal Access Token method** - it's faster:

1. Create token as described in Option 1
2. Run: `git push -u origin merge-gedfix-architecture`  
3. Username: `isndotbiz`
4. Password: **[paste your token]**

Your 184 files and 7 professional commits will upload to https://github.com/isndotbiz/ged
