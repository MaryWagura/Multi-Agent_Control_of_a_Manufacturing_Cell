import requests
import time

print("Sending manufacturing order to Source 2 API...")

# URL for Source 2 (Port 8002)
url = "http://localhost:8002/spawn"
part_type = "type2"

response = requests.post(url, data=part_type)

print(f"Status Code: {response.status_code}")
print(f"Response: {response.text}")