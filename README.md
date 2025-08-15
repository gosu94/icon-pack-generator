# Icon Pack Generator

A Spring Boot web application for generating custom icon packs using AI. This application uses the Flux-1-Kontext-Pro model to generate icons based on user descriptions and automatically crops them into individual icons.

## Features

- **Web Interface**: Clean, responsive UI with form-based icon generation
- **AI Integration**: Integrated with Flux-1-Kontext-Pro model for high-quality icon generation
- **Flexible Icon Counts**: Generate 9 or 18 icons at once
- **Automated Processing**: Automatic image cropping to extract individual icons from generated grids
- **Real-time Results**: Live display of generated icons in the browser

## Prerequisites

- Java 21 or higher
- Gradle 7.x or higher
- Flux AI API key (or compatible AI service)

## Setup

1. **Clone the repository** (if not already done)

2. **Configure Fal.ai API Settings**
   - Open `src/main/resources/application.properties`
   - Replace the placeholder values with your actual fal.ai API configuration:
   ```properties
   fal.ai.api-key=your-fal-api-key-here
   fal.ai.model-endpoint=fal-ai/flux/dev
   ```
   
   Alternatively, you can set the API key as an environment variable:
   ```bash
   export FAL_API_KEY=your-actual-fal-api-key-here
   ```

3. **Build the application**
   ```bash
   ./gradlew build
   ```

4. **Run the application**
   ```bash
   ./gradlew bootRun
   ```

5. **Access the application**
   - Open your browser and go to `http://localhost:8080`

## Usage

### Web Interface

1. **Fill in the form**:
   - **General Theme Description**: Describe the overall theme for your icon pack (e.g., "Social media icons in flat design style")
   - **Number of Icons**: Choose between 9 icons (3x3 grid) or 18 icons (2x 3x3 grids)
   - **Individual Descriptions** (Optional): Provide specific descriptions for each icon

2. **Generate Icons**:
   - Click "Generate Icons" to start the process
   - Wait for the AI to generate and process your icons (may take up to 2 minutes)
   - View the results in the right panel

### API Endpoints

The application also provides REST API endpoints:

- `POST /generate` - Generate icons programmatically
  ```json
  {
    "generalDescription": "Social media icons",
    "iconCount": 9,
    "individualDescriptions": ["Facebook", "Twitter", "Instagram", ...]
  }
  ```

## Architecture

### Backend Components

- **Controller Layer**: `IconPackController` - Handles web requests and API endpoints
- **Service Layer**: 
  - `IconGenerationService` - Orchestrates the icon generation process
  - `AIModelService` - Abstract interface for AI model integration
  - `FalAiModelService` - Concrete implementation using fal.ai Java client
  - `IconExportService` - Handles ZIP file creation for icon downloads
  - `ImageProcessingService` - Handles image cropping and processing
  - `PromptGenerationService` - Generates optimized prompts for AI models
- **Model Layer**: DTOs and configuration classes
- **Configuration**: `FalAiConfig` - Configures the fal.ai client

### Frontend Components

- **Thymeleaf Templates**: Server-side rendered HTML templates
- **Bootstrap CSS**: Responsive styling and components
- **Vanilla JavaScript**: Dynamic form handling and API interaction

## Configuration

Key configuration options in `application.properties`:

```properties
# Fal.ai Settings
fal.ai.api-key=${FAL_API_KEY:your-fal-api-key-here}
fal.ai.model-endpoint=fal-ai/flux/dev
fal.ai.timeout-seconds=120
fal.ai.max-retries=3
fal.ai.image-size=landscape_4_3
fal.ai.num-images=1
fal.ai.enable-safety-checker=true

# Server Settings
server.port=8080

# Development Settings
spring.thymeleaf.cache=false
spring.web.resources.cache.period=0
```

## Extending the Application

### Adding New AI Models

1. Implement the `AIModelService` interface
2. Add your implementation as a Spring component
3. Configure the service in your application properties
4. Update the `FalAiConfig` if using a different client library

### Customizing Prompts

Modify the `PromptGenerationService` to customize how prompts are generated for the AI model.

### Adding Background Removal

The application is designed with future rembg integration in mind. You can extend the `ImageProcessingService` to add background removal capabilities.

## Troubleshooting

### Common Issues

1. **API Key Issues**: Ensure your fal.ai API key is correctly set and has sufficient credits
2. **Timeout Errors**: Increase the timeout settings if generation takes longer than expected
3. **Image Processing Errors**: Check that the AI is generating properly formatted grid images

### Debugging Tools

The application includes several debugging endpoints and features:

1. **Health Check Endpoint**: Visit `http://localhost:8080/health/fal-ai` to test your fal.ai API connection
2. **Enhanced Error Messages**: Detailed error messages with specific troubleshooting steps
3. **API Key Validation**: Automatic validation of API key format and configuration
4. **Detailed Logging**: Set logging level to DEBUG for comprehensive API call logging

Example health check response:
```json
{
  "service": "fal.ai",
  "status": "UP",
  "available": true,
  "model": "fal-ai/flux/dev",
  "message": "Fal.ai API is responding correctly",
  "timestamp": 1755181142508
}
```

### Logging

Enable debug logging to troubleshoot issues:
```properties
logging.level.com.gosu.icon_pack_generator=DEBUG
logging.level.org.springframework.web.reactive.function.client=DEBUG
```

## Development

### Building for Production

```bash
./gradlew clean build
java -jar build/libs/icon-pack-generator-0.0.1-SNAPSHOT.jar
```

### Running Tests

```bash
./gradlew test
```

## Future Enhancements

- Background removal using rembg
- Multiple output formats (SVG, different sizes)
- Batch processing capabilities
- User authentication and project management
- Template and style presets
- Integration with additional AI models

## License

This project is licensed under the MIT License - see the LICENSE file for details.
