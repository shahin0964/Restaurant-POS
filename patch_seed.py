import re

with open('app/src/main/java/com/restaurant/pos/data/repository/AuthRepository.kt', 'r') as f:
    content = f.read()

old_seed = """    suspend fun seedDefaultUserIfNeeded() {
        val existingAdmin = userDao.getUserByEmailOrPhone("admin@dynamic.com")
        if (existingAdmin == null) {
            val defaultUser = UserEntity(
                emailOrPhone = "admin@dynamic.com",
                name = "Admin",
                role = "Administrator",
                passwordHash = "admin123",
                isCurrentSession = false,
                isActive = true
            )
            userDao.insertUser(defaultUser)
        }
    }"""

new_seed = """    suspend fun seedDefaultUserIfNeeded() {
        // No demo/fake users should be seeded.
    }"""

content = content.replace(old_seed, new_seed)

with open('app/src/main/java/com/restaurant/pos/data/repository/AuthRepository.kt', 'w') as f:
    f.write(content)
